package com.innercosmos.payments.channel;

import com.innercosmos.entity.PaymentOrder;
import com.innercosmos.payments.PaymentCallbackVerifier;
import com.innercosmos.payments.PaymentLedgerService;
import com.innercosmos.payments.PaymentOrderService;
import com.innercosmos.payments.entitlement.EntitlementStateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CP-45 callback ingestion: decode → verify → validate → record → entitle, every step
 * fail-closed and in that order, per the blueprint's server-side rules (服务端校验签名／
 * 商户／订单／金额／币种；客户端只能展示不能宣告支付成功). Nothing reaches the append-only
 * ledger unless the CP-47 HMAC verification passed, the merchant identity matches operator
 * configuration, the raw channel status maps to a known ledger fact, AND the order claim
 * validates against the server-side order catalog: an unknown order, a wrong channel or a
 * drifting amount is rejected — a verified-but-contradicted amount is kept as a DISPUTED
 * fact (drift stays visible, never absorbed). Unknown statuses are quarantined for a human
 * 查单 (不确定支付先查单，禁止盲重扣) and the channel keeps retrying — recording is
 * idempotent once resolved.
 *
 * <p>Entitlement wiring: an accepted payment grants the order's product to the order's
 * user (the 扣款未解锁 gap closes server-side); a FULL refund revokes it. A partial refund
 * keeps the entitlement — pro-rata downgrade is an explicit product decision, not a silent
 * default. Duplicate callbacks are idempotent end-to-end (ledger + entitlement audit).
 *
 * <p>Configuration is operator-injected (environment/secret manager), blank by default:
 * a channel with no configured merchant rejects everything rather than trusting payloads.
 */
@Service
public class ChannelCallbackIngestService {

    public enum Outcome {
        ACCEPTED, REJECTED_SIGNATURE, REJECTED_MERCHANT, REJECTED_ORDER, REJECTED_AMOUNT,
        QUARANTINED_STATUS, MALFORMED, UNKNOWN_PROVIDER
    }

    public record IngestResult(Outcome outcome, String providerEventId, String orderId, String detail) {
    }

    private final PaymentCallbackVerifier verifier;
    private final PaymentLedgerService ledger;
    private final PaymentOrderService orders;
    private final EntitlementStateService entitlements;
    private final Map<String, ChannelCallbackAdapter> adapters = new HashMap<>();
    private final String wechatMchid;
    private final String alipayAppId;

    @Autowired
    public ChannelCallbackIngestService(
            PaymentCallbackVerifier verifier,
            PaymentLedgerService ledger,
            PaymentOrderService orders,
            EntitlementStateService entitlements,
            List<ChannelCallbackAdapter> adapterList,
            @Value("${inner-cosmos.payments.channels.wechatpay.mchid:}") String wechatMchid,
            @Value("${inner-cosmos.payments.channels.alipay.app-id:}") String alipayAppId) {
        this.verifier = verifier;
        this.ledger = ledger;
        this.orders = orders;
        this.entitlements = entitlements;
        for (ChannelCallbackAdapter adapter : adapterList) {
            this.adapters.put(adapter.provider(), adapter);
        }
        this.wechatMchid = wechatMchid == null ? "" : wechatMchid.trim();
        this.alipayAppId = alipayAppId == null ? "" : alipayAppId.trim();
    }

    /** The adapter registered for a provider slug, or null — used by the controller to format acks. */
    public ChannelCallbackAdapter adapter(String provider) {
        return adapters.get(provider);
    }

    public IngestResult ingest(String provider, String rawBody, Map<String, String> headers) {
        ChannelCallbackAdapter adapter = adapters.get(provider);
        if (adapter == null) {
            return new IngestResult(Outcome.UNKNOWN_PROVIDER, null, null, provider);
        }
        ChannelCallback callback;
        try {
            callback = adapter.decode(rawBody, headers);
        } catch (ChannelDecodeException e) {
            return new IngestResult(Outcome.MALFORMED, null, null, e.getMessage());
        }
        // Merchant identity: server-side configured value wins; blank config fails closed.
        if (!merchantMatches(callback)) {
            return new IngestResult(Outcome.REJECTED_MERCHANT, callback.providerEventId(),
                    callback.orderId(), "merchant mismatch or unconfigured channel");
        }
        // Signature: CP-47 kernel — timestamp-bound, freshness-bounded, constant-time.
        if (!verifier.verify(callback.timestamp(), callback.canonicalBody(), callback.signatureHex())) {
            return new IngestResult(Outcome.REJECTED_SIGNATURE, callback.providerEventId(),
                    callback.orderId(), "signature verification failed");
        }
        // Status: only enumerated facts proceed; everything else is quarantined.
        Optional<String> ledgerType = ChannelEventType.ledgerEventType(provider, callback.rawStatus());
        if (ledgerType.isEmpty()) {
            return new IngestResult(Outcome.QUARANTINED_STATUS, callback.providerEventId(),
                    callback.orderId(), "unmapped raw status " + callback.rawStatus()
                    + " — 查单 before any ledger write");
        }
        // Order catalog: the app — not the callback — decides amount, channel and buyer.
        PaymentOrder order = orders.find(callback.orderId());
        if (order == null || order.userId == null || order.productId == null
                || !provider.equals(order.channel)) {
            return new IngestResult(Outcome.REJECTED_ORDER, callback.providerEventId(),
                    callback.orderId(), "unknown order or channel mismatch — nothing to reconcile against");
        }
        String eventType = ledgerType.get();
        if ("PAYMENT_SUCCEEDED".equals(eventType)) {
            if (order.expectedAmountCents == null
                    || callback.amountCents() != order.expectedAmountCents) {
                ledger.recordDisputed(callback.providerEventId(), provider, callback.orderId(),
                        eventType, callback.amountCents(), callback.occurredAt());
                return new IngestResult(Outcome.REJECTED_AMOUNT, callback.providerEventId(),
                        callback.orderId(), "amount " + callback.amountCents()
                        + " contradicts order expectation " + order.expectedAmountCents
                        + " — recorded DISPUTED");
            }
        } else {
            // Over-refund: refunding more than the net the order ever collected is drift.
            long netBeforeRefund = ledger.orderNetCents(callback.orderId());
            if (callback.amountCents() > netBeforeRefund) {
                ledger.recordDisputed(callback.providerEventId(), provider, callback.orderId(),
                        eventType, callback.amountCents(), callback.occurredAt());
                return new IngestResult(Outcome.REJECTED_AMOUNT, callback.providerEventId(),
                        callback.orderId(), "refund " + callback.amountCents()
                        + " exceeds collected net " + netBeforeRefund + " — recorded DISPUTED");
            }
        }
        // Idempotent per provider event: a channel retry acks the existing row.
        ledger.record(callback.providerEventId(), provider, callback.orderId(), eventType,
                callback.amountCents(), callback.occurredAt());
        if ("PAYMENT_SUCCEEDED".equals(eventType)) {
            entitlements.onPaymentSucceeded(order.userId, order.productId, provider,
                    callback.orderId(), callback.providerEventId(), callback.occurredAt());
        } else if (ledger.orderNetCents(callback.orderId()) == 0) {
            // Net collected is now zero — the buyer holds nothing they paid for: revoke.
            // Partial refunds keep the entitlement: pro-rata downgrade is an explicit
            // product decision, not a silent default.
            entitlements.onRefundSucceeded(order.userId, order.productId,
                    callback.providerEventId(), callback.occurredAt());
        }
        return new IngestResult(Outcome.ACCEPTED, callback.providerEventId(), callback.orderId(),
                eventType);
    }

    private boolean merchantMatches(ChannelCallback callback) {
        if (callback.merchantId() == null) {
            return false;
        }
        return switch (callback.provider()) {
            case WeChatPayCallbackAdapter.PROVIDER -> !wechatMchid.isBlank()
                    && wechatMchid.equals(callback.merchantId());
            case AlipayCallbackAdapter.PROVIDER -> !alipayAppId.isBlank()
                    && alipayAppId.equals(callback.merchantId());
            default -> false;
        };
    }
}
