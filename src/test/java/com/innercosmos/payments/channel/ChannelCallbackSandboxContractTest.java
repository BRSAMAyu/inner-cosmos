package com.innercosmos.payments.channel;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.PaymentEvent;
import com.innercosmos.mapper.PaymentEventMapper;
import com.innercosmos.payments.PaymentCallbackVerifier;
import com.innercosmos.payments.PaymentLedgerService;
import com.innercosmos.payments.channel.ChannelCallbackIngestService.Outcome;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CP-45/46 channel sandbox contract, no real channel touched: WeChat Pay v3 and Alipay
 * notify shapes decode onto the CP-47 kernel; verification fails closed on blank
 * secret/unconfigured merchant/tampered body/stale timestamp; only enumerated raw
 * statuses reach the ledger (everything else quarantined for 查单); duplicate notifies
 * ack idempotently without double-crediting. The endpoint wiring test proves the
 * anonymous callback path is reachable (permitAll + CSRF-exempt) while still failing
 * closed with the context's blank operator configuration.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChannelCallbackSandboxContractTest {

    private static final String SECRET = "sandbox-test-secret";
    private static final String MCHID = "1900000109";
    private static final String APP_ID = "2026000000000001";
    private static final Clock NOW = Clock.fixed(Instant.parse("2026-09-12T12:00:00Z"), ZoneOffset.UTC);
    private static final String FRESH_TS = String.valueOf(NOW.instant().getEpochSecond());

    @Autowired PaymentLedgerService ledger;
    @Autowired PaymentEventMapper mapper;
    @Autowired MockMvc mockMvc;

    private ChannelCallbackIngestService configuredIngest(String wechatMchid, String alipayAppId) {
        return new ChannelCallbackIngestService(
                new PaymentCallbackVerifier(SECRET, NOW), ledger,
                List.of(new WeChatPayCallbackAdapter(), new AlipayCallbackAdapter()),
                wechatMchid, alipayAppId);
    }

    // ---------- WeChat Pay (v3 notify envelope) ----------

    @Test
    void wechatPaymentNotifyDecodesVerifiesAndRecords() {
        String order = "WX-OK-" + System.nanoTime();
        String eventId = "wx-ev-" + order;
        String body = wechatBody(eventId, "TRANSACTION.SUCCESS", order, 4900, null);
        var result = configuredIngest(MCHID, APP_ID)
                .ingest("wechatpay", body, wechatHeaders(FRESH_TS, sign(body)));
        assertEquals(Outcome.ACCEPTED, result.outcome());
        assertEquals("PAYMENT_SUCCEEDED", result.detail());
        PaymentEvent row = mapper.selectOne(new QueryWrapper<PaymentEvent>()
                .eq("provider_event_id", eventId));
        assertEquals("wechatpay", row.provider);
        assertEquals(order, row.orderId);
        assertEquals(4900L, row.amountCents);
        assertEquals("PAYMENT_SUCCEEDED", row.eventType);
    }

    @Test
    void wechatRefundNotifyUsesRefundAmountNeverTheOrderTotal() {
        String order = "WX-RF-" + System.nanoTime();
        String eventId = "wx-ev-" + order;
        // Partial refund of 19.00 out of a 49.00 order: the ledger must net 4900-1900.
        var ingest = configuredIngest(MCHID, APP_ID);
        ingest.ingest("wechatpay", wechatBody("wx-pay-" + order, "TRANSACTION.SUCCESS", order, 4900, null),
                wechatHeaders(FRESH_TS, sign(wechatBody("wx-pay-" + order, "TRANSACTION.SUCCESS", order, 4900, null))));
        String refundBody = wechatBody(eventId, "REFUND.SUCCESS", order, 4900, 1900L);
        assertEquals(Outcome.ACCEPTED, ingest
                .ingest("wechatpay", refundBody, wechatHeaders(FRESH_TS, sign(refundBody))).outcome());
        PaymentEvent row = mapper.selectOne(new QueryWrapper<PaymentEvent>()
                .eq("provider_event_id", eventId));
        assertEquals(1900L, row.amountCents, "refund fact carries the refunded sum, not the order total");
        assertEquals(3000L, ledger.orderNetCents(order));
    }

    @Test
    void wechatRefundWithoutRefundAmountIsMalformedNeverGuessed() {
        // amount.refund absent on a REFUND.SUCCESS: fail closed rather than reading total.
        String order = "WX-RF0-" + System.nanoTime();
        String body = "{\"id\":\"wx-ev-" + order + "\",\"event_type\":\"REFUND.SUCCESS\",\"resource\":{"
                + "\"mchid\":\"" + MCHID + "\",\"out_trade_no\":\"" + order + "\","
                + "\"amount\":{\"total\":4900,\"currency\":\"CNY\"},"
                + "\"success_time\":\"2026-09-12T12:00:01+08:00\"}}";
        var result = configuredIngest(MCHID, APP_ID)
                .ingest("wechatpay", body, wechatHeaders(FRESH_TS, sign(body)));
        assertEquals(Outcome.MALFORMED, result.outcome());
        assertNull(mapper.selectOne(new QueryWrapper<PaymentEvent>()
                .eq("provider_event_id", "wx-ev-" + order)));
    }

    @Test
    void wechatDuplicateNotifyAcksIdempotentlyWithoutDoubleCredit() {
        String order = "WX-DUP-" + System.nanoTime();
        String eventId = "wx-ev-" + order;
        var ingest = configuredIngest(MCHID, APP_ID);
        String body = wechatBody(eventId, "TRANSACTION.SUCCESS", order, 4900, null);
        Map<String, String> headers = wechatHeaders(FRESH_TS, sign(body));
        assertEquals(Outcome.ACCEPTED, ingest.ingest("wechatpay", body, headers).outcome());
        assertEquals(Outcome.ACCEPTED, ingest.ingest("wechatpay", body, headers).outcome(),
                "channel retry of the same notify id is an ack, not a second credit");
        assertEquals(4900L, ledger.orderNetCents(order));
    }

    @Test
    void wechatFailClosedOnTamperStaleTimestampWrongMerchantAndBlankConfig() {
        String order = "WX-NEG-" + System.nanoTime();
        String body = wechatBody("wx-ev-" + order, "TRANSACTION.SUCCESS", order, 4900, null);
        var ingest = configuredIngest(MCHID, APP_ID);
        // Tampered body: signature was computed over different bytes.
        String tampered = body.replace("4900", "9900");
        assertEquals(Outcome.REJECTED_SIGNATURE, ingest
                .ingest("wechatpay", tampered, wechatHeaders(FRESH_TS, sign(body))).outcome());
        // Stale timestamp: valid signature over a timestamp outside the freshness window.
        String staleTs = "1600000000";
        String staleBody = wechatBody("wx-stale-" + order, "TRANSACTION.SUCCESS", order, 4900, null);
        assertEquals(Outcome.REJECTED_SIGNATURE, ingest
                .ingest("wechatpay", staleBody, wechatHeaders(staleTs, sign(staleTs + "." + staleBody)))
                .outcome());
        // Wrong merchant id in payload.
        String thiefBody = wechatBody("wx-thief-" + order, "TRANSACTION.SUCCESS", order, 4900, null)
                .replace(MCHID, "1900000999");
        assertEquals(Outcome.REJECTED_MERCHANT, ingest
                .ingest("wechatpay", thiefBody, wechatHeaders(FRESH_TS, sign(thiefBody))).outcome());
        // Blank operator configuration fails closed even for a perfectly signed notify.
        assertEquals(Outcome.REJECTED_MERCHANT, configuredIngest("", APP_ID)
                .ingest("wechatpay", body, wechatHeaders(FRESH_TS, sign(body))).outcome());
        assertNull(mapper.selectOne(new QueryWrapper<PaymentEvent>().eq("order_id", order)),
                "no rejection path may leave a ledger row");
    }

    @Test
    void wechatNonTerminalAndUnknownStatusesAreQuarantinedForManualCheck() {
        String order = "WX-Q-" + System.nanoTime();
        var ingest = configuredIngest(MCHID, APP_ID);
        for (String rawStatus : List.of("TRANSACTION.CLOSED", "REFUND.ABNORMAL", "SOMETHING.NEW")) {
            String body = wechatBody("wx-" + rawStatus + "-" + order, rawStatus, order, 4900, null);
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
                wechatBody("wx-nohdr", "TRANSACTION.SUCCESS", "WX-NH", 4900, null), Map.of()).outcome());
        // Non-CNY currency is outside the ledger's unit system.
        String usd = wechatBody("wx-usd", "TRANSACTION.SUCCESS", "WX-USD", 4900, null)
                .replace("\"CNY\"", "\"USD\"");
        assertEquals(Outcome.MALFORMED, ingest.ingest("wechatpay", usd,
                wechatHeaders(FRESH_TS, sign(usd))).outcome());
    }

    // ---------- Alipay (form-encoded notify) ----------

    @Test
    void alipayNotifyDecodesVerifiesAndRecordsInCnyMinorUnits() {
        var ingest = configuredIngest(MCHID, APP_ID);
        // TRADE_SUCCESS 49.00 yuan → 4900 cents.
        String order = "ALI-OK-" + System.nanoTime();
        String successBody = alipayBody(order, "TRADE_SUCCESS", "49.00", null);
        assertEquals(Outcome.ACCEPTED, ingest
                .ingest("alipay", successBody, Map.of()).outcome());
        PaymentEvent row = mapper.selectOne(new QueryWrapper<PaymentEvent>()
                .eq("provider_event_id", "ali-ev-" + order + "-TRADE_SUCCESS"));
        assertEquals(4900L, row.amountCents);
        assertEquals("PAYMENT_SUCCEEDED", row.eventType);
        // TRADE_FINISHED is also a final capture.
        String finished = alipayBody("ALI-FIN-" + System.nanoTime(), "TRADE_FINISHED", "49.00", null);
        assertEquals(Outcome.ACCEPTED, ingest.ingest("alipay", finished, Map.of()).outcome());
        // Partial refund 19.50 yuan → 1950 cents REFUND_SUCCEEDED.
        String refundOrder = "ALI-RF-" + System.nanoTime();
        ingest.ingest("alipay", alipayBody(refundOrder, "TRADE_SUCCESS", "49.00", null), Map.of());
        String refund = alipayBody(refundOrder, "REFUND_SUCCESS", "49.00", "19.50");
        assertEquals(Outcome.ACCEPTED, ingest.ingest("alipay", refund, Map.of()).outcome());
        PaymentEvent refundRow = mapper.selectOne(new QueryWrapper<PaymentEvent>()
                .eq("provider_event_id", "ali-ev-" + refundOrder + "-REFUND_SUCCESS"));
        assertEquals(1950L, refundRow.amountCents);
        assertEquals(2950L, ledger.orderNetCents(refundOrder));
    }

    @Test
    void alipaySignatureCoversTheSortedCanonicalBody() {
        var ingest = configuredIngest(MCHID, APP_ID);
        String order = "ALI-TAMPER-" + System.nanoTime();
        // Valid signature over the notify's canonical form, then inject an extra parameter
        // the signer never saw — canonicalization must notice, not just the timestamp.
        String signed = alipayBody(order, "TRADE_SUCCESS", "49.00", null);
        String tampered = signed + "&extra_injected=1";
        assertEquals(Outcome.REJECTED_SIGNATURE, ingest.ingest("alipay", tampered, Map.of()).outcome());
        // Amount rewritten AFTER signing: the signature still covers total_amount=49.00.
        String signed49 = alipayBody(order, "TRADE_SUCCESS", "49.00", null);
        String amountTampered = signed49.replace("total_amount=49.00", "total_amount=0.01");
        assertEquals(Outcome.REJECTED_SIGNATURE, ingest.ingest("alipay", amountTampered, Map.of()).outcome());
        assertNull(mapper.selectOne(new QueryWrapper<PaymentEvent>().eq("order_id", order)));
    }

    @Test
    void alipayNonTerminalAndUnknownStatusesAreQuarantined() {
        var ingest = configuredIngest(MCHID, APP_ID);
        for (String status : List.of("WAIT_BUYER_PAY", "TRADE_CLOSED", "FUTURE_STATUS")) {
            String order = "ALI-Q-" + status + "-" + System.nanoTime();
            assertEquals(Outcome.QUARANTINED_STATUS, ingest
                    .ingest("alipay", alipayBody(order, status, "49.00", null), Map.of()).outcome(),
                    status + " must not become a ledger fact");
            assertNull(mapper.selectOne(new QueryWrapper<PaymentEvent>().eq("order_id", order)));
        }
    }

    @Test
    void alipayFailsClosedOnWrongAppIdBlankConfigAndMalformedForm() {
        var ingest = configuredIngest(MCHID, APP_ID);
        String order = "ALI-NEG-" + System.nanoTime();
        String thief = alipayBody(order, "TRADE_SUCCESS", "49.00", null)
                .replace("app_id=" + APP_ID, "app_id=2099000000000009");
        assertEquals(Outcome.REJECTED_MERCHANT, ingest.ingest("alipay", thief, Map.of()).outcome());
        assertEquals(Outcome.REJECTED_MERCHANT, configuredIngest(MCHID, "")
                .ingest("alipay", alipayBody(order, "TRADE_SUCCESS", "49.00", null), Map.of()).outcome());
        assertEquals(Outcome.MALFORMED, ingest.ingest("alipay", "notifyonly&bro=ken", Map.of()).outcome());
        assertNull(mapper.selectOne(new QueryWrapper<PaymentEvent>().eq("order_id", order)));
    }

    // ---------- cross-channel plumbing ----------

    @Test
    void unknownProviderSlugAndBlankSecretAreRejected() {
        assertEquals(Outcome.UNKNOWN_PROVIDER, configuredIngest(MCHID, APP_ID)
                .ingest("stripe", "{}", Map.of()).outcome());
        // Blank secret verifier: even a perfectly signed, correctly merchaunted notify fails.
        String order = "BLANK-SEC-" + System.nanoTime();
        String body = wechatBody("wx-bs-" + order, "TRANSACTION.SUCCESS", order, 4900, null);
        var blankSecretIngest = new ChannelCallbackIngestService(
                new PaymentCallbackVerifier("", NOW), ledger,
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
        String body = wechatBody("wx-ctx", "TRANSACTION.SUCCESS", "WX-CTX", 4900, null);
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

    private static Map<String, String> wechatHeaders(String timestamp, String signature) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Wechatpay-Timestamp", timestamp);
        headers.put("Wechatpay-Nonce", "nonce");
        headers.put("Wechatpay-Signature", signature);
        return headers;
    }

    private static String wechatBody(String eventId, String eventType, String orderId,
                                     long total, Long refund) {
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
        if (refundFee != null) {
            params.put("total_amount", totalAmount);
            params.put("refund_fee", refundFee);
        } else {
            params.put("total_amount", totalAmount);
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
