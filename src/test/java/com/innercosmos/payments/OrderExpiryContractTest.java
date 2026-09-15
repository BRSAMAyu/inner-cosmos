package com.innercosmos.payments;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.PaymentEvent;
import com.innercosmos.entity.PaymentOrder;
import com.innercosmos.mapper.PaymentEventMapper;
import com.innercosmos.mapper.PaymentOrderMapper;
import com.innercosmos.payments.channel.AlipayCallbackAdapter;
import com.innercosmos.payments.channel.ChannelCallbackIngestService;
import com.innercosmos.payments.channel.ChannelCallbackIngestService.Outcome;
import com.innercosmos.payments.channel.WeChatPayCallbackAdapter;
import com.innercosmos.payments.entitlement.EntitlementStateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-45 §2-21 order-expiry contract: every order carries expiresAt (TTL 2h default);
 * past it, an unpaid CREATED order flips to the EXPIRED terminal — lazily on any
 * callback/query path and by the scheduled batch sweep, both idempotent (a racing pair
 * can never flip twice). A late payment/refund notify for an expired order is REFUSED
 * (REJECTED_EXPIRED): never fabricated into a success, never granted an entitlement,
 * never counted in the net — but recorded as an informational REJECTED ledger fact so
 * money the channel claims moved against a closed order stays visible for 对账. Channel
 * retries ack idempotently. User ids stay in the 88xxxxxxx segment (shared H2).
 */
@SpringBootTest
class OrderExpiryContractTest {

    private static final String SECRET = "expiry-test-secret";
    private static final String MCHID = "1900000109";
    private static final String APP_ID = "2026000000000001";
    private static final String PRODUCT = "pro.monthly";
    private static final Clock NOW = Clock.fixed(Instant.parse("2026-09-12T12:00:00Z"), ZoneOffset.UTC);
    private static final String FRESH_TS = String.valueOf(NOW.instant().getEpochSecond());
    private static final AtomicLong USERS = new AtomicLong(881_000_000L);

    @Autowired PaymentOrderService orders;
    @Autowired PaymentOrderMapper orderMapper;
    @Autowired PriceVersionService prices;
    @Autowired PaymentLedgerService ledger;
    @Autowired PaymentEventMapper eventMapper;
    @Autowired EntitlementStateService entitlements;

    private static long uniqueUser() {
        return USERS.incrementAndGet();
    }

    /** Orders born already overdue: expiresAt 60s in the past, status CREATED. */
    private PaymentOrder expiredOrder() {
        PaymentOrderService factory = new PaymentOrderService(
                orderMapper, prices, Duration.ofSeconds(-60), Clock.systemUTC());
        PaymentOrder order = factory.createOrder(uniqueUser(), PRODUCT, "wechatpay");
        PaymentOrder raw = orderMapper.selectOne(new QueryWrapper<PaymentOrder>()
                .eq("order_id", order.orderId));
        assertEquals("CREATED", raw.status, "expiry is lazy — creation must not pre-flip");
        assertTrue(raw.expiresAt.isBefore(java.time.LocalDateTime.now(ZoneOffset.UTC)));
        return raw;
    }

    private ChannelCallbackIngestService ingest() {
        return new ChannelCallbackIngestService(
                new PaymentCallbackVerifier(SECRET, NOW), ledger, orders, entitlements, null,
                List.of(new WeChatPayCallbackAdapter(), new AlipayCallbackAdapter()),
                MCHID, APP_ID);
    }

    private PaymentOrder reload(String orderId) {
        // Raw mapper read: shows the persisted terminal state without triggering anything.
        return orderMapper.selectOne(new QueryWrapper<PaymentOrder>().eq("order_id", orderId));
    }

    private String entitlementState(long userId) {
        return entitlements.snapshot(userId).stream()
                .filter(v -> PRODUCT.equals(v.productId()))
                .map(EntitlementStateService.EntitlementView::state)
                .findFirst().orElse("NONE");
    }

    @Test
    void lazyFindFlipsAnOverdueCreatedOrderToExpiredTerminalIdempotently() {
        PaymentOrder overdue = expiredOrder();
        // The lazy path: a plain lookup notices the deadline and flips the terminal state.
        PaymentOrder found = orders.find(overdue.orderId);
        assertEquals("EXPIRED", found.status, "an overdue CREATED order expires on read");
        assertEquals("EXPIRED", reload(overdue.orderId).status);
        // Idempotent: a second lookup (or a racing sweep) has nothing left to flip.
        assertEquals("EXPIRED", orders.find(overdue.orderId).status);
        assertEquals("EXPIRED", reload(overdue.orderId).status);
    }

    @Test
    void latePaymentCallbackOnExpiredOrderIsRefusedRecordedAndNeverCredited() {
        PaymentOrder expired = expiredOrder();
        long victim = expired.userId;
        String eventId = "wx-late-" + expired.orderId;
        String body = wechatBody(eventId, "TRANSACTION.SUCCESS", expired.orderId,
                expired.expectedAmountCents, null);
        var result = ingest().ingest("wechatpay", body, wechatHeaders(FRESH_TS, sign(body)));
        assertEquals(Outcome.REJECTED_EXPIRED, result.outcome(),
                "a validly signed late payment on an expired order must be refused");
        assertEquals("EXPIRED", reload(expired.orderId).status, "terminal state holds");
        // The refusal is a visible ledger fact — informational, never net money.
        PaymentEvent fact = eventMapper.selectOne(new QueryWrapper<PaymentEvent>()
                .eq("provider_event_id", eventId));
        assertNotNull(fact, "拒收必须留下账本事件");
        assertEquals("EXPIRED_ORDER_CALLBACK", fact.eventType);
        assertEquals("REJECTED", fact.status);
        assertEquals(expired.expectedAmountCents, fact.amountCents);
        assertEquals(0L, ledger.orderNetCents(expired.orderId),
                "a refused callback can never dent the net");
        assertEquals("NONE", entitlementState(victim),
                "过期订单不伪造支付成功 — no entitlement may appear");
        // Channel retry of the same notify: ack, not a second row, not a flip-flop.
        var retry = ingest().ingest("wechatpay", body, wechatHeaders(FRESH_TS, sign(body)));
        assertEquals(Outcome.REJECTED_EXPIRED, retry.outcome());
        assertEquals(1, eventMapper.selectList(new QueryWrapper<PaymentEvent>()
                .eq("provider_event_id", eventId)).size(), "idempotent per provider event");
        assertEquals("EXPIRED", reload(expired.orderId).status);
        assertEquals("NONE", entitlementState(victim));
    }

    @Test
    void lateRefundCallbackOnAnExpiredOrderIsAlsoRefusedAsAFact() {
        PaymentOrder expired = expiredOrder();
        String eventId = "wx-late-refund-" + expired.orderId;
        String body = wechatBody(eventId, "REFUND.SUCCESS", expired.orderId,
                expired.expectedAmountCents, 1000L);
        assertEquals(Outcome.REJECTED_EXPIRED, ingest()
                .ingest("wechatpay", body, wechatHeaders(FRESH_TS, sign(body))).outcome());
        assertEquals("REJECTED", eventMapper.selectOne(new QueryWrapper<PaymentEvent>()
                .eq("provider_event_id", eventId)).status);
        assertEquals(0L, ledger.orderNetCents(expired.orderId),
                "the refused refund must not subtract from a zero net");
    }

    @Test
    void scheduledSweepExpiresOverdueBatchAndLeavesFreshOrdersAlone() {
        PaymentOrder overdue = expiredOrder();
        PaymentOrder fresh = orders.createOrder(uniqueUser(), PRODUCT, "wechatpay");
        PaymentOrderService sweeper = new PaymentOrderService(
                orderMapper, prices, Duration.ofHours(2), Clock.systemUTC());
        int flipped = sweeper.expireOverdue();
        assertTrue(flipped >= 1, "the sweep must flip due orders (flipped=" + flipped + ")");
        assertEquals("EXPIRED", reload(overdue.orderId).status);
        assertEquals("CREATED", reload(fresh.orderId).status,
                "an order inside its TTL must never be swept");
        // Idempotent batch: a repeated sweep finds nothing new to flip on these rows.
        sweeper.expireOverdue();
        assertEquals("EXPIRED", reload(overdue.orderId).status);
        assertEquals("CREATED", reload(fresh.orderId).status);
    }

    @Test
    void unexpiredOrdersKeepTheNormalAcceptPathGreen() {
        PaymentOrder fresh = orders.createOrder(uniqueUser(), PRODUCT, "wechatpay");
        assertEquals("CREATED", orders.find(fresh.orderId).status,
                "a fresh lookup must not expire anything");
        assertNotNull(fresh.expiresAt, "every new order carries a deadline");
        String eventId = "wx-fresh-" + fresh.orderId;
        String body = wechatBody(eventId, "TRANSACTION.SUCCESS", fresh.orderId,
                fresh.expectedAmountCents, null);
        assertEquals(Outcome.ACCEPTED, ingest()
                .ingest("wechatpay", body, wechatHeaders(FRESH_TS, sign(body))).outcome());
        assertEquals(fresh.expectedAmountCents, ledger.orderNetCents(fresh.orderId));
        assertEquals("ACTIVE", entitlementState(fresh.userId),
                "unexpired chain regression: payment still unlocks");
    }

    // ---------- sandbox fixtures (same shapes as ChannelCallbackSandboxContractTest) ----------

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

    private static String sign(String canonicalBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of()
                    .formatHex(mac.doFinal((FRESH_TS + "." + canonicalBody)
                            .getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
