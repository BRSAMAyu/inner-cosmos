package com.innercosmos.payments;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.PaymentOrder;
import com.innercosmos.entity.PriceVersion;
import com.innercosmos.mapper.PaymentOrderMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-45 §2-21 price-version contract: the price table is append-only — a change retires
 * the old version and inserts a new one, amounts are never edited in place — so an order
 * created before a change keeps the exact version (and amount) it was priced at, forever
 * traceable. Orders placed while a switch is in flight each pin their own version; no
 * order ever observes a half-applied price. User ids stay in the 88xxxxxxx segment,
 * disjoint from the other payments suites (shared H2 across the test JVM).
 */
@SpringBootTest
class PriceVersionContractTest {

    private static final String PRODUCT = "pro.monthly";
    private static final long SEED_CENTS = 2500L;
    private static final AtomicLong USERS = new AtomicLong(880_000_000L);

    @Autowired PaymentOrderService orders;
    @Autowired PriceVersionService prices;
    @Autowired PaymentOrderMapper orderMapper;

    /** The suite mutates the shared sandbox price; every test leaves ACTIVE 2500 behind. */
    @AfterEach
    void restoreSandboxPrice() {
        PriceVersion active = prices.currentActiveOrSeed(PRODUCT);
        if (active.amountCents != SEED_CENTS) {
            prices.changePrice(PRODUCT, SEED_CENTS, "CNY");
        }
    }

    private static long uniqueUser() {
        return USERS.incrementAndGet();
    }

    private void assertPinnedConsistently(PaymentOrder order) {
        assertNotNull(order.priceVersionId, "order must pin the version it was priced at");
        PriceVersion pinned = prices.findVersion(order.priceVersionId);
        assertNotNull(pinned, "pinned version row must stay traceable");
        assertEquals(order.expectedAmountCents, pinned.amountCents,
                "order amount is the pinned version's amount — never a live re-read");
    }

    @Test
    void firstOrderSeedsVersionOneAndPriceChangesRetireNotRewrite() {
        // First order lazily seeds the auditable version 1 at the sandbox default.
        PaymentOrder first = orders.createOrder(uniqueUser(), PRODUCT, "wechatpay");
        assertEquals(SEED_CENTS, first.expectedAmountCents);
        PriceVersion v1 = prices.currentActiveOrSeed(PRODUCT);
        assertEquals(1, v1.version);
        assertEquals("ACTIVE", v1.status);
        assertEquals(v1.id, first.priceVersionId);

        // 改价 = new row: old amount untouched, old row RETIRED, new row ACTIVE.
        PriceVersion v2 = prices.changePrice(PRODUCT, 2900L, "CNY");
        assertNotEquals(v1.id, v2.id);
        assertEquals(2, v2.version);
        assertEquals("ACTIVE", v2.status);
        PriceVersion v1After = prices.findVersion(v1.id);
        assertEquals("RETIRED", v1After.status, "old version retires, never disappears");
        assertNotNull(v1After.retiredAt);
        assertEquals(SEED_CENTS, v1After.amountCents, "retired amount is immutable");

        // The OLD order still carries the OLD amount and still traces to version 1.
        PaymentOrder firstAfter = orderMapper.selectOne(new QueryWrapper<PaymentOrder>()
                .eq("order_id", first.orderId));
        assertEquals(SEED_CENTS, firstAfter.expectedAmountCents, "改价不改旧订单");
        assertEquals(v1.id, firstAfter.priceVersionId);
        assertPinnedConsistently(firstAfter);

        // New orders pin version 2's amount; a further change keeps the whole chain.
        PaymentOrder second = orders.createOrder(uniqueUser(), PRODUCT, "alipay");
        assertEquals(2900L, second.expectedAmountCents);
        assertEquals(v2.id, second.priceVersionId);
        PriceVersion v3 = prices.changePrice(PRODUCT, 3100L, "CNY");
        assertEquals(3, v3.version);
        assertEquals("RETIRED", prices.findVersion(v2.id).status);
        assertEquals(2900L, prices.findVersion(v2.id).amountCents);
        assertEquals(SEED_CENTS, prices.findVersion(v1.id).amountCents);
        // The second order survives the further change with its pin intact.
        PaymentOrder secondAfter = orderMapper.selectOne(new QueryWrapper<PaymentOrder>()
                .eq("order_id", second.orderId));
        assertEquals(2900L, secondAfter.expectedAmountCents);
        assertPinnedConsistently(secondAfter);
    }

    @Test
    void nonPositivePriceChangesAreRejectedWithoutTouchingTheTable() {
        PriceVersion before = prices.currentActiveOrSeed(PRODUCT);
        boolean rejected = false;
        try {
            prices.changePrice(PRODUCT, 0L, "CNY");
        } catch (Exception expected) {
            rejected = true;
        }
        assertTrue(rejected, "non-positive price must be a catalog error");
        assertEquals(before.id, prices.currentActiveOrSeed(PRODUCT).id,
                "a rejected change leaves the ACTIVE version untouched");
    }

    @Test
    void ordersPlacedAcrossAConcurrentPriceSwitchEachLockTheirOwnVersion() throws Exception {
        PriceVersion initial = prices.currentActiveOrSeed(PRODUCT);
        AtomicBoolean running = new AtomicBoolean(true);
        CountDownLatch warmed = new CountDownLatch(2);
        List<PaymentOrder> created = new CopyOnWriteArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        for (int t = 0; t < 2; t++) {
            pool.submit(() -> {
                warmed.countDown();
                while (running.get()) {
                    created.add(orders.createOrder(uniqueUser(), PRODUCT, "wechatpay"));
                }
                return null;
            });
        }
        assertTrue(warmed.await(10, TimeUnit.SECONDS));
        Thread.sleep(150); // orders pin the initial version
        PriceVersion switched = prices.changePrice(PRODUCT, 2900L, "CNY");
        Thread.sleep(150); // orders pin the new version
        running.set(false);
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertTrue(created.size() >= 4, "expected orders on both sides of the switch");
        long initialOrders = created.stream()
                .filter(o -> initial.id.equals(o.priceVersionId)).count();
        long switchedOrders = created.stream()
                .filter(o -> switched.id.equals(o.priceVersionId)).count();
        assertTrue(initialOrders >= 1 && switchedOrders >= 1,
                "both versions must capture orders across the switch (initial="
                        + initialOrders + ", switched=" + switchedOrders + ")");
        // Every order is internally consistent with the version it pinned, whatever the
        // clock said when it was created — no order re-reads a live price.
        for (PaymentOrder order : created) {
            assertPinnedConsistently(order);
        }
        // The chain ends ACTIVE at the switch amount, RETIRED at the initial one.
        assertEquals(2900L, prices.currentActiveOrSeed(PRODUCT).amountCents);
        assertEquals("RETIRED", prices.findVersion(initial.id).status);
        assertEquals(SEED_CENTS, prices.findVersion(initial.id).amountCents);
    }
}
