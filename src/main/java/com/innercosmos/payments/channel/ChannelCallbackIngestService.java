package com.innercosmos.payments.channel;

import com.innercosmos.payments.PaymentCallbackVerifier;
import com.innercosmos.payments.PaymentLedgerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CP-45 callback ingestion: decode → verify → record, every step fail-closed and in that
 * order, per the blueprint's server-side rules (服务端校验签名／商户／订单／金额／币种；客户端只能
 * 展示不能宣告支付成功). Nothing reaches the append-only ledger unless the CP-47 HMAC
 * verification passed AND the merchant identity matches operator configuration AND the raw
 * channel status maps to a known ledger fact. Unknown statuses are quarantined for a human
 * 查单 (不确定支付先查单，禁止盲重扣) — the channel keeps retrying, which is exactly the wanted
 * behaviour, because recording is idempotent once ops resolves the event.
 *
 * <p>Configuration is operator-injected (environment/secret manager), blank by default:
 * a channel with no configured merchant rejects everything rather than trusting payloads.
 */
@Service
public class ChannelCallbackIngestService {

    public enum Outcome {
        ACCEPTED, REJECTED_SIGNATURE, REJECTED_MERCHANT, QUARANTINED_STATUS, MALFORMED, UNKNOWN_PROVIDER
    }

    public record IngestResult(Outcome outcome, String providerEventId, String orderId, String detail) {
    }

    private final PaymentCallbackVerifier verifier;
    private final PaymentLedgerService ledger;
    private final Map<String, ChannelCallbackAdapter> adapters = new HashMap<>();
    private final String wechatMchid;
    private final String alipayAppId;

    @Autowired
    public ChannelCallbackIngestService(
            PaymentCallbackVerifier verifier,
            PaymentLedgerService ledger,
            List<ChannelCallbackAdapter> adapterList,
            @Value("${inner-cosmos.payments.channels.wechatpay.mchid:}") String wechatMchid,
            @Value("${inner-cosmos.payments.channels.alipay.app-id:}") String alipayAppId) {
        this.verifier = verifier;
        this.ledger = ledger;
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
        // Status: only enumerated facts are recorded; everything else is quarantined.
        Optional<String> ledgerType = ChannelEventType.ledgerEventType(provider, callback.rawStatus());
        if (ledgerType.isEmpty()) {
            return new IngestResult(Outcome.QUARANTINED_STATUS, callback.providerEventId(),
                    callback.orderId(), "unmapped raw status " + callback.rawStatus()
                    + " — 查单 before any ledger write");
        }
        // Idempotent per provider event: a channel retry acks the existing row.
        ledger.record(callback.providerEventId(), provider, callback.orderId(),
                ledgerType.get(), callback.amountCents(), callback.occurredAt());
        return new IngestResult(Outcome.ACCEPTED, callback.providerEventId(), callback.orderId(),
                ledgerType.get());
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
