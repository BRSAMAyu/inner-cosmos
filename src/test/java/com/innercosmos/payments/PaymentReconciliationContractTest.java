package com.innercosmos.payments;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.PaymentEvent;
import com.innercosmos.mapper.PaymentEventMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-47 payment reconciliation contract, no real channel: verification is fail-closed and
 * replay-resistant; ledger recording is idempotent per provider event; out-of-order
 * callbacks (refund before payment, payment after refund) net to the same truth; a
 * mismatched reconciliation flags DISPUTED instead of absorbing drift.
 */
@SpringBootTest
class PaymentReconciliationContractTest {

    private static final String SECRET = "test-only-secret";

    @Autowired PaymentLedgerService ledger;
    @Autowired PaymentEventMapper mapper;

    @Test
    void verificationIsFailClosedFreshnessBoundAndTamperEvident() throws Exception {
        Clock now = Clock.fixed(Instant.parse("2026-09-12T12:00:00Z"), ZoneOffset.UTC);
        PaymentCallbackVerifier verifier = new PaymentCallbackVerifier(SECRET, now);
        String staleTs = "1760000000";
        String freshTs = String.valueOf(now.instant().getEpochSecond());
        String body = "orderId=O-1&amount=4900&event=PAYMENT_SUCCEEDED";

        assertTrue(verifier.verify(freshTs, body, sign(SECRET, freshTs + "." + body)));
        // Fail-closed: blank secret rejects everything; tampered body/key invalidates signature.
        assertFalse(new PaymentCallbackVerifier("", now)
                .verify(freshTs, body, sign(SECRET, freshTs + "." + body)));
        assertFalse(verifier.verify(freshTs, body + "0", sign(SECRET, freshTs + "." + body)));
        assertFalse(verifier.verify(freshTs, body, sign("wrong", freshTs + "." + body)));
        // Replay resistance: stale timestamp rejected even with a valid signature; the
        // timestamp is INSIDE the signed payload, so it cannot be rewritten.
        assertFalse(verifier.verify(staleTs, body, sign(SECRET, staleTs + "." + body)));
        assertFalse(verifier.verify(staleTs, body, sign(SECRET, freshTs + "." + body)));
        // Malformed inputs never throw open.
        assertFalse(verifier.verify("abc", body, "zz"));
        assertFalse(verifier.verify(freshTs, body, null));
    }

    @Test
    void duplicateCallbackIsAnIdempotentAckNeverADoubleCredit() {
        String providerEvent = "pe-dup-" + System.nanoTime();
        ledger.record(providerEvent, "test", "O-DUP", "PAYMENT_SUCCEEDED", 4900,
                LocalDateTime.now(ZoneOffset.UTC));
        PaymentEvent again = ledger.record(providerEvent, "test", "O-DUP", "PAYMENT_SUCCEEDED",
                4900, LocalDateTime.now(ZoneOffset.UTC));
        assertEquals("RECORDED", again.status);
        assertEquals(4900, ledger.orderNetCents("O-DUP"), "duplicate callback credited once");
    }

    @Test
    void outOfOrderCallbacksNetToTheSameTruthInBothArrivalOrders() {
        String orderA = "O-OO-A-" + System.nanoTime();
        String orderB = "O-OO-B-" + System.nanoTime();
        // Order A: payment first, then refund.
        ledger.record("pe-a1-" + orderA, "test", orderA, "PAYMENT_SUCCEEDED", 4900,
                LocalDateTime.now(ZoneOffset.UTC));
        ledger.record("pe-a2-" + orderA, "test", orderA, "REFUND_SUCCEEDED", 4900,
                LocalDateTime.now(ZoneOffset.UTC));
        // Order B: refund lands FIRST (chargeback raced the capture), then the payment.
        ledger.record("pe-b2-" + orderB, "test", orderB, "REFUND_SUCCEEDED", 4900,
                LocalDateTime.now(ZoneOffset.UTC));
        ledger.record("pe-b1-" + orderB, "test", orderB, "PAYMENT_SUCCEEDED", 4900,
                LocalDateTime.now(ZoneOffset.UTC));
        assertEquals(0, ledger.orderNetCents(orderA));
        assertEquals(0, ledger.orderNetCents(orderB),
                "arrival order must not change the reconciled net");
    }

    @Test
    void mismatchedReconciliationFlagsDisputedInsteadOfAbsorbingDrift() {
        String order = "O-MIS-" + System.nanoTime();
        ledger.record("pe-m1-" + order, "test", order, "PAYMENT_SUCCEEDED", 4900,
                LocalDateTime.now(ZoneOffset.UTC));
        // Channel settled only 4800 (missing 100 cents the provider never reported).
        assertFalse(ledger.reconcile(order, 4800));
        assertEquals("DISPUTED", mapper.selectList(new QueryWrapper<PaymentEvent>()
                        .eq("order_id", order)).get(0).status,
                "drift is visible, never silently absorbed");
        String ok = "O-OK-" + System.nanoTime();
        ledger.record("pe-ok-" + ok, "test", ok, "PAYMENT_SUCCEEDED", 4900,
                LocalDateTime.now(ZoneOffset.UTC));
        assertTrue(ledger.reconcile(ok, 4900));
    }

    private static String sign(String secret, String payload) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
