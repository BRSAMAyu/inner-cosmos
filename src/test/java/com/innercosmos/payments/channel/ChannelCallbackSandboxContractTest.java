package com.innercosmos.payments.channel;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.PaymentEvent;
import com.innercosmos.entity.PaymentOrder;
import com.innercosmos.mapper.PaymentEventMapper;
import com.innercosmos.payments.PaymentCallbackVerifier;
import com.innercosmos.payments.PaymentLedgerService;
import com.innercosmos.payments.PaymentOrderService;
import com.innercosmos.payments.channel.ChannelCallbackIngestService.Outcome;
import com.innercosmos.payments.entitlement.EntitlementStateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CP-45/46 channel sandbox contract, no real channel: WeChat Pay v3 and Alipay notify
 * shapes decode onto the CP-47 kernel; verification fails closed on blank secret/
 * unconfigured merchant/tampered body/stale timestamp; only enumerated raw statuses may
 * become ledger facts; the server-side ORDER CATALOG is the amount/channel authority —
 * unknown orders, drifting amounts and over-refunds are rejected (drift kept as DISPUTED
 * facts, never absorbed); accepted payments grant the order's product to the order's user
 * and full refunds revoke it (扣款未解锁 closes server-side). The endpoint wiring test
 * proves the anonymous callback path is reachable while still failing closed with the
 * context's blank operator configuration.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChannelCallbackSandboxContractTest {

    private static final String SECRET = "sandbox-test-secret";
    private static final String MCHID = "1900000109";
    private static final String APP_ID = "2026000000000001";
    private static final String PRODUCT = "pro.monthly";
    private static final Clock NOW = Clock.fixed(Instant.parse("2026-09-12T12:00:00Z"), ZoneOffset.UTC);
    private static final String FRESH_TS = String.valueOf(NOW.instant().getEpochSecond());
    // Disjoint from EntitlementStateMachineContractTest's user range (900M) so the two
    // suites' UNIQUE(user_id, product_id) rows never collide in the same test JVM.
    private static final AtomicLong USERS = new AtomicLong(950_000_000L);

    @Autowired PaymentLedgerService ledger;
    @Autowired PaymentOrderService orders;
    @Autowired EntitlementStateService entitlements;
    @Autowired PaymentEventMapper mapper;
    @Autowired MockMvc mockMvc;

    private ChannelCallbackIngestService configuredIngest(String wechatMchid, String alipayAppId) {
        return new ChannelCallbackIngestService(
                new PaymentCallbackVerifier(SECRET, NOW), ledger, orders, entitlements,
                List.of(new WeChatPayCallbackAdapter(), new AlipayCallbackAdapter()),
                wechatMchid, alipayAppId);
    }

    private static long uniqueUser() {
        return USERS.incrementAndGet();
    }

    private PaymentOrder newWechatOrder() {
        return orders.createOrder(uniqueUser(), PRODUCT, "wechatpay");
    }

    private PaymentOrder newAlipayOrder() {
        return orders.createOrder(uniqueUser(), PRODUCT, "alipay");
    }

    // ---------- WeChat Pay (v3 notify envelope) ----------

    @Test
    void wechatPaymentNotifyDecodesVerifiesRecordsAndGrants() {
        PaymentOrder order = newWechatOrder();
        String eventId = "wx-ev-" + order.orderId;
        String body = wechatBody(eventId, "TRANSACTION.SUCCESS", order.orderId,
                order.expectedAmountCents, null);
        var result = configuredIngest(MCHID, APP_ID)
                .ingest("wechatpay", body, wechatHeaders(FRESH_TS, sign(body)));
        assertEquals(Outcome.ACCEPTED, result.outcome());
        PaymentEvent row = mapper.selectOne(new QueryWrapper<PaymentEvent>()
                .eq("provider_event_id", eventId));
        assertEquals("wechatpay", row.provider);
        assertEquals(order.orderId, row.orderId);
        assertEquals(order.expectedAmountCents, row.amountCents);
        assertEquals("PAYMENT_SUCCEEDED", row.eventType);
        // Payment fact → entitlement granted to the ORDER's user: 扣款未解锁 closes server-side.
        var view = entitlements.snapshot(order.userId).stream()
                .filter(v -> PRODUCT.equals(v.productId())).findFirst().orElseThrow();
        assertEquals("ACTIVE", view.state(), "accepted payment must unlock the entitlement");
    }

    @Test
    void wechatRefundNotifyUsesRefundAmountAndPartialRefundsKeepTheEntitlement() {
        PaymentOrder order = newWechatOrder();
        var ingest = configuredIngest(MCHID, APP_ID);
        String payBody = wechatBody("wx-pay-" + order.orderId, "TRANSACTION.SUCCESS",
                order.orderId, order.expectedAmountCents, null);
        assertEquals(Outcome.ACCEPTED, ingest
                .ingest("wechatpay", payBody, wechatHeaders(FRESH_TS, sign(payBody))).outcome());
        // Partial refund of 10.00 out of a 25.00 order: the ledger nets, the entitlement stays.
        String refundBody = wechatBody("wx-refund-" + order.orderId, "REFUND.SUCCESS",
                order.orderId, order.expectedAmountCents, 1000L);
        assertEquals(Outcome.ACCEPTED, ingest
                .ingest("wechatpay", refundBody, wechatHeaders(FRESH_TS, sign(refundBody))).outcome());
        PaymentEvent row = mapper.selectOne(new QueryWrapper<PaymentEvent>()
                .eq("provider_event_id", "wx-refund-" + order.orderId));
        assertEquals(1000L, row.amountCents, "refund fact carries the refunded sum, not the order total");
        assertEquals(order.expectedAmountCents - 1000L, ledger.orderNetCents(order.orderId));
        assertEquals("ACTIVE", entitlementState(order.userId),
                "partial refund keeps the entitlement — pro-rata downgrade is a product decision");
    }

    @Test
    void wechatRefundWithoutRefundAmountIsMalformedNeverGuessed() {
        String order = "WX-RF0-" + System.nanoTime();
        String body = "{\"id\":\"wx-ev-" + order + "\",\"event_type\":\"REFUND.SUCCESS\",\"resource\":{"
                + "\"mchid\":\"" + MCHID + "\",\"out_trade_no\":\"" + order + "\","
                + "\"amount\":{\"total\":2500,\"currency\":\"CNY\"},"
                + "\"success_time\":\"2026-09-12T12:00:01+08:00\"}}";
        var result = configuredIngest(MCHID, APP_ID)
                .ingest("wechatpay", body, wechatHeaders(FRESH_TS, sign(body)));
        assertEquals(Outcome.MALFORMED, result.outcome());
        assertNull(mapper.selectOne(new QueryWrapper<PaymentEvent>()
                .eq("provider_event_id", "wx-ev-" + order)));
    }

    @Test
    void wechatDuplicateNotifyAcksIdempotentlyWithoutDoubleCreditOrDoubleGrant() {
        PaymentOrder order = newWechatOrder();
        String eventId = "wx-ev-" + order.orderId;
        var ingest = configuredIngest(MCHID, APP_ID);
        String body = wechatBody(eventId, "TRANSACTION.SUCCESS", order.orderId,
                order.expectedAmountCents, null);
        Map<String, String> headers = wechatHeaders(FRESH_TS, sign(body));
        assertEquals(Outcome.ACCEPTED, ingest.ingest("wechatpay", body, headers).outcome());
        assertEquals(Outcome.ACCEPTED, ingest.ingest("wechatpay", body, headers).outcome(),
                "channel retry of the same notify id is an ack, not a second credit");
        assertEquals(order.expectedAmountCents, ledger.orderNetCents(order.orderId));
    }

    @Test
    void wechatFailClosedOnTamperStaleTimestampWrongMerchantAndBlankConfig() {
        long user = uniqueUser();
        String order = "WX-NEG-" + System.nanoTime();
        String body = wechatBody("wx-ev-" + order, "TRANSACTION.SUCCESS", order, 2500L, null);
        var ingest = configuredIngest(MCHID, APP_ID);
        // Tampered body: signature was computed over different bytes.
        String tampered = body.replace("2500", "9900");
        assertEquals(Outcome.REJECTED_SIGNATURE, ingest
                .ingest("wechatpay", tampered, wechatHeaders(FRESH_TS, sign(body))).outcome());
        // Stale timestamp: valid signature over a timestamp outside the freshness window.
        String staleTs = "1600000000";
        String staleBody = wechatBody("wx-stale-" + order, "TRANSACTION.SUCCESS", order, 2500L, null);
        assertEquals(Outcome.REJECTED_SIGNATURE, ingest
                .ingest("wechatpay", staleBody, wechatHeaders(staleTs, sign(staleTs + "." + staleBody)))
                .outcome());
        // Wrong merchant id in payload.
        String thiefBody = wechatBody("wx-thief-" + order, "TRANSACTION.SUCCESS", order, 2500L, null)
                .replace(MCHID, "1900000999");
        assertEquals(Outcome.REJECTED_MERCHANT, ingest
                .ingest("wechatpay", thiefBody, wechatHeaders(FRESH_TS, sign(thiefBody))).outcome());
        // Blank operator configuration fails closed even for a perfectly signed notify.
        assertEquals(Outcome.REJECTED_MERCHANT, configuredIngest("", APP_ID)
                .ingest("wechatpay", body, wechatHeaders(FRESH_TS, sign(body))).outcome());
        assertNull(mapper.selectOne(new QueryWrapper<PaymentEvent>().eq("order_id", order)),
                "no rejection path may leave a ledger row");
        assertTrue(entitlements.snapshot(user).stream().noneMatch(v -> PRODUCT.equals(v.productId())));
    }

    @Test
    void wechatNonTerminalAndUnknownStatusesAreQuarantinedForManualCheck() {
        String order = "WX-Q-" + System.nanoTime();
        var ingest = configuredIngest(MCHID, APP_ID);
        for (String rawStatus : List.of("TRANSACTION.CLOSED", "REFUND.ABNORMAL", "SOMETHING.NEW")) {
            String body = wechatBody("wx-" + rawStatus + "-" + order, rawStatus, order, 2500L, null);
            assertEquals(Outcome.QUARANTINED_STATUS, ingest
                    .ingest("wechatpay", body, wechatHeaders(FRESH_TS, sign(body))).outcome(),
                    rawStatus + " must not become a ledger fact without 查单");
        }
        assertNull(mapper.selectOne(new QueryWrapper<PaymentEvent>().eq("order_id", order)));
    }

    @Test
    void wechatMalformedBodiesNeverReachVerification() {
        var ingest = configuredIngest(MCHID, APP_ID);
        assertEquals(Outcome.MALFORMED, ingest.ingest("wechatpay", "not json",
                wechatHeaders(FRESH_TS, "00")).outcome());
        assertEquals(Outcome.MALFORMED, ingest.ingest("wechatpay", "{}",
                wechatHeaders(FRESH_TS, "00")).outcome());
        // Missing signature headers entirely.
        assertEquals(Outcome.MALFORMED, ingest.ingest("wechatpay",
                wechatBody("wx-nohdr", "TRANSACTION.SUCCESS", "WX-NH", 2500L, null), Map.of()).outcome());
        // Non-CNY currency is outside the ledger's unit system.
        String usd = wechatBody("wx-usd", "TRANSACTION.SUCCESS", "WX-USD", 2500L, null)
                .replace("\"CNY\"", "\"USD\"");
        assertEquals(Outcome.MALFORMED, ingest.ingest("wechatpay", usd,
                wechatHeaders(FRESH_TS, sign(usd))).outcome());
    }

    // ---------- Alipay (form-encoded notify) ----------

    @Test
    void alipayNotifyDecodesVerifiesRecordsAndGrantsInCnyMinorUnits() {
        var ingest = configuredIngest(MCHID, APP_ID);
        PaymentOrder order = newAlipayOrder();
        String successBody = alipayBody(order.orderId, "TRADE_SUCCESS",
                yuan(order.expectedAmountCents), null);
        assertEquals(Outcome.ACCEPTED, ingest.ingest("alipay", successBody, Map.of()).outcome());
        PaymentEvent row = mapper.selectOne(new QueryWrapper<PaymentEvent>()
                .eq("provider_event_id", "ali-ev-" + order.orderId + "-TRADE_SUCCESS"));
        assertEquals(order.expectedAmountCents, row.amountCents);
        assertEquals("PAYMENT_SUCCEEDED", row.eventType);
        assertEquals("ACTIVE", entitlementState(order.userId));
        // TRADE_FINISHED is also a final capture, on its own order.
        PaymentOrder finished = newAlipayOrder();
        String finishedBody = alipayBody(finished.orderId, "TRADE_FINISHED",
                yuan(finished.expectedAmountCents), null);
        assertEquals(Outcome.ACCEPTED, ingest.ingest("alipay", finishedBody, Map.of()).outcome());
        // Partial refund 10.00 yuan → 1000 cents REFUND_SUCCEEDED.
        String refund = alipayBody(order.orderId, "REFUND_SUCCESS",
                yuan(order.expectedAmountCents), "10.00");
        assertEquals(Outcome.ACCEPTED, ingest.ingest("alipay", refund, Map.of()).outcome());
        PaymentEvent refundRow = mapper.selectOne(new QueryWrapper<PaymentEvent>()
                .eq("provider_event_id", "ali-ev-" + order.orderId + "-REFUND_SUCCESS"));
        assertEquals(1000L, refundRow.amountCents);
        assertEquals(order.expectedAmountCents - 1000L, ledger.orderNetCents(order.orderId));
    }

    @Test
    void alipaySignatureCoversTheSortedCanonicalBody() {
        var ingest = configuredIngest(MCHID, APP_ID);
        PaymentOrder order = newAlipayOrder();
        // Valid signature over the notify's canonical form, then inject an extra parameter
        // the signer never saw — canonicalization must notice, not just the timestamp.
        String signed = alipayBody(order.orderId, "TRADE_SUCCESS",
                yuan(order.expectedAmountCents), null);
        String tampered = signed + "&extra_injected=1";
        assertEquals(Outcome.REJECTED_SIGNATURE, ingest.ingest("alipay", tampered, Map.of()).outcome());
        // Amount rewritten AFTER signing: the signature still covers the original amount.
        String amountTampered = signed.replace(
                "total_amount=" + yuan(order.expectedAmountCents), "total_amount=0.01");
        assertEquals(Outcome.REJECTED_SIGNATURE, ingest.ingest("alipay", amountTampered, Map.of()).outcome());
        assertNull(mapper.selectOne(new QueryWrapper<PaymentEvent>().eq("order_id", order.orderId)));
    }

    @Test
    void alipayNonTerminalAndUnknownStatusesAreQuarantined() {
        var ingest = configuredIngest(MCHID, APP_ID);
        for (String status : List.of("WAIT_BUYER_PAY", "TRADE_CLOSED", "FUTURE_STATUS")) {
            String order = "ALI-Q-" + status + "-" + System.nanoTime();
            assertEquals(Outcome.QUARANTINED_STATUS, ingest
                    .ingest("alipay", alipayBody(order, status, "25.00", null), Map.of()).outcome(),
                    status + " must not become a ledger fact");
            assertNull(mapper.selectOne(new QueryWrapper<PaymentEvent>().eq("order_id", order)));
        }
    }

    @Test
    void alipayFailsClosedOnWrongAppIdBlankConfigAndMalformedForm() {
        var ingest = configuredIngest(MCHID, APP_ID);
        String order = "ALI-NEG-" + System.nanoTime();
        String thief = alipayBody(order, "TRADE_SUCCESS", "25.00", null)
                .replace("app_id=" + APP_ID, "app_id=2099000000000009");
        assertEquals(Outcome.REJECTED_MERCHANT, ingest.ingest("alipay", thief, Map.of()).outcome());
        assertEquals(Outcome.REJECTED_MERCHANT, configuredIngest(MCHID, "")
                .ingest("alipay", alipayBody(order, "TRADE_SUCCESS", "25.00", null), Map.of()).outcome());
        assertEquals(Outcome.MALFORMED, ingest.ingest("alipay", "notifyonly&bro=ken", Map.of()).outcome());
        assertNull(mapper.selectOne(new QueryWrapper<PaymentEvent>().eq("order_id", order)));
    }

    // ---------- the order catalog is the authority (CP-45) ----------

    @Test
    void orderCatalogRejectsUnknownOrdersDriftingAmountsAndOverRefunds() {
        var ingest = configuredIngest(MCHID, APP_ID);
        // Unknown order: nothing to reconcile against — reject, record nothing.
        String ghost = "IC-NEVER-CREATED-" + System.nanoTime();
        String ghostBody = wechatBody("wx-ghost", "TRANSACTION.SUCCESS", ghost, 2500L, null);
        assertEquals(Outcome.REJECTED_ORDER, ingest
                .ingest("wechatpay", ghostBody, wechatHeaders(FRESH_TS, sign(ghostBody))).outcome());
        assertNull(mapper.selectOne(new QueryWrapper<PaymentEvent>().eq("order_id", ghost)));

        // Amount drift: correctly signed payment for an amount we never asked for —
        // kept as a DISPUTED fact, never acknowledged as a clean payment, no grant.
        PaymentOrder order = newWechatOrder();
        String drift = wechatBody("wx-drift-" + order.orderId, "TRANSACTION.SUCCESS",
                order.orderId, order.expectedAmountCents + 2400, null);
        var driftResult = ingest.ingest("wechatpay", drift, wechatHeaders(FRESH_TS, sign(drift)));
        assertEquals(Outcome.REJECTED_AMOUNT, driftResult.outcome());
        PaymentEvent disputed = mapper.selectOne(new QueryWrapper<PaymentEvent>()
                .eq("provider_event_id", "wx-drift-" + order.orderId));
        assertEquals("DISPUTED", disputed.status, "drift stays visible, never absorbed");
        assertTrue(entitlements.snapshot(order.userId).stream()
                .noneMatch(v -> PRODUCT.equals(v.productId())), "no grant on contradicted amount");

        // Channel mismatch: an Alipay callback claiming a WeChat order id.
        PaymentOrder wechatOrder = newWechatOrder();
        String wrongChannel = alipayBody(wechatOrder.orderId, "TRADE_SUCCESS", "25.00", null);
        assertEquals(Outcome.REJECTED_ORDER, ingest.ingest("alipay", wrongChannel, Map.of()).outcome());

        // Over-refund: refunding more than the order ever collected is drift too.
        PaymentOrder refundOrder = newWechatOrder();
        String payBody = wechatBody("wx-or-pay", "TRANSACTION.SUCCESS", refundOrder.orderId,
                refundOrder.expectedAmountCents, null);
        assertEquals(Outcome.ACCEPTED, ingest
                .ingest("wechatpay", payBody, wechatHeaders(FRESH_TS, sign(payBody))).outcome());
        String overRefund = wechatBody("wx-or-refund", "REFUND.SUCCESS", refundOrder.orderId,
                refundOrder.expectedAmountCents, refundOrder.expectedAmountCents + 500);
        assertEquals(Outcome.REJECTED_AMOUNT, ingest
                .ingest("wechatpay", overRefund, wechatHeaders(FRESH_TS, sign(overRefund))).outcome());
        assertEquals("DISPUTED", mapper.selectOne(new QueryWrapper<PaymentEvent>()
                .eq("provider_event_id", "wx-or-refund")).status);
        assertEquals("ACTIVE", entitlementState(refundOrder.userId),
                "an over-refund does not revoke — it is disputed drift for ops");
    }

    @Test
    void fullRefundRevokesTheEntitlementPartialKeepsIt() {
        var ingest = configuredIngest(MCHID, APP_ID);
        PaymentOrder order = newWechatOrder();
        String pay = wechatBody("wx-fr-pay", "TRANSACTION.SUCCESS", order.orderId,
                order.expectedAmountCents, null);
        ingest.ingest("wechatpay", pay, wechatHeaders(FRESH_TS, sign(pay)));
        assertEquals("ACTIVE", entitlementState(order.userId));
        String fullRefund = wechatBody("wx-fr-refund", "REFUND.SUCCESS", order.orderId,
                order.expectedAmountCents, order.expectedAmountCents);
        assertEquals(Outcome.ACCEPTED, ingest
                .ingest("wechatpay", fullRefund, wechatHeaders(FRESH_TS, sign(fullRefund))).outcome());
        assertEquals(0L, ledger.orderNetCents(order.orderId));
        assertEquals("REVOKED", entitlementState(order.userId),
                "full refund revokes the entitlement (退款后撤销)");
    }

    // ---------- cross-channel plumbing ----------

    @Test
    void unknownProviderSlugAndBlankSecretAreRejected() {
        assertEquals(Outcome.UNKNOWN_PROVIDER, configuredIngest(MCHID, APP_ID)
                .ingest("stripe", "{}", Map.of()).outcome());
        // Blank secret verifier: even a perfectly signed, correctly merchaunted notify fails.
        String order = "BLANK-SEC-" + System.nanoTime();
        String body = wechatBody("wx-bs-" + order, "TRANSACTION.SUCCESS", order, 2500L, null);
        var blankSecretIngest = new ChannelCallbackIngestService(
                new PaymentCallbackVerifier("", NOW), ledger, orders, entitlements,
                List.of(new WeChatPayCallbackAdapter(), new AlipayCallbackAdapter()),
                MCHID, APP_ID);
        assertEquals(Outcome.REJECTED_SIGNATURE, blankSecretIngest
                .ingest("wechatpay", body, wechatHeaders(FRESH_TS, sign(body))).outcome());
        assertNull(mapper.selectOne(new QueryWrapper<PaymentEvent>().eq("order_id", order)));
    }

    /**
     * Endpoint wiring: the anonymous path is reachable (no session, no CSRF token —
     * permitAll + CSRF exemption in SecurityConfig) and still fails closed under the
     * context's blank operator configuration, answering with the channel's FAIL ack.
     */
    @Test
    void callbackEndpointIsReachableAnonymouslyButFailsClosedWithBlankContextConfig() throws Exception {
        String body = wechatBody("wx-ctx", "TRANSACTION.SUCCESS", "WX-CTX", 2500L, null);
        mockMvc.perform(post("/api/payments/callbacks/wechatpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Wechatpay-Timestamp", FRESH_TS)
                        .header("Wechatpay-Signature", sign(body))
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FAIL"));
        mockMvc.perform(post("/api/payments/callbacks/alipay")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("garbage=1"))
                .andExpect(status().isBadRequest());
        assertTrue(mapper.selectList(new QueryWrapper<PaymentEvent>()
                        .eq("order_id", "WX-CTX")).isEmpty(),
                "unconfigured context must record nothing");
    }

    // ---------- sandbox fixtures ----------

    private String entitlementState(long userId) {
        return entitlements.snapshot(userId).stream()
                .filter(v -> PRODUCT.equals(v.productId()))
                .map(EntitlementStateService.EntitlementView::state)
                .findFirst().orElse("NONE");
    }

    private static String yuan(long cents) {
        return java.math.BigDecimal.valueOf(cents, 2).toPlainString();
    }

    private static Map<String, String> wechatHeaders(String timestamp, String signature) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Wechatpay-Timestamp", timestamp);
        headers.put("Wechatpay-Nonce", "nonce");
        headers.put("Wechatpay-Signature", signature);
        return headers;
    }

    private static String wechatBody(String eventId, String eventType, String orderId,
                                     Long total, Long refund) {
        String amount = refund == null
                ? "\"total\":" + total + ",\"currency\":\"CNY\""
                : "\"total\":" + total + ",\"refund\":" + refund + ",\"currency\":\"CNY\"";
        return "{\"id\":\"" + eventId + "\",\"event_type\":\"" + eventType + "\",\"resource\":{"
                + "\"mchid\":\"" + MCHID + "\",\"out_trade_no\":\"" + orderId + "\","
                + "\"transaction_id\":\"wx-txn-" + eventId + "\","
                + "\"amount\":{" + amount + "},"
                + "\"success_time\":\"2026-09-12T12:00:01+08:00\"}}";
    }

    /** Form body over the full sandbox parameter set, signed over the canonical sorted form. */
    private static String alipayBody(String orderId, String tradeStatus, String totalAmount,
                                     String refundFee) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("notify_id", "ali-ev-" + orderId + "-" + tradeStatus);
        params.put("trade_status", tradeStatus);
        params.put("out_trade_no", orderId);
        params.put("trade_no", "ali-txn-" + orderId);
        params.put("app_id", APP_ID);
        params.put("gmt_payment", "2026-09-12 20:00:01");
        params.put("total_amount", totalAmount);
        if (refundFee != null) {
            params.put("refund_fee", refundFee);
        }
        params.put("sandbox_timestamp", FRESH_TS);
        String canonical = AlipayCallbackAdapter.canonicalBody(params);
        params.put("sandbox_sign", sign(canonical));
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    private static String sign(String canonicalBody) {
        return signPayload(FRESH_TS + "." + canonicalBody);
    }

    private static String signPayload(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
