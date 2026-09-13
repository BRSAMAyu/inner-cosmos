package com.innercosmos.ai.gateway;

import com.innercosmos.common.ErrorCode;
import com.innercosmos.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-17 gateway governor semantics: the in-flight gate refuses with 429-style
 * GATEWAY_BUSY (retryable, nothing spent), releases on every path, and the per-call
 * deadline interrupts slow provider work fail-closed instead of letting it hang or
 * half-succeed.
 */
class GatewayCallGovernorTest {

    private GatewayCallGovernor governor(int maxConcurrency, long deadlineMs) {
        GatewayCallGovernor governor = new GatewayCallGovernor();
        org.springframework.test.util.ReflectionTestUtils.setField(governor, "maxConcurrency", maxConcurrency);
        org.springframework.test.util.ReflectionTestUtils.setField(governor, "callDeadlineMs", deadlineMs);
        return governor;
    }

    @Test
    void fullGateRefusesWithGatewayBusyAndIsRetryableAfterRelease() {
        GatewayCallGovernor governor = governor(1, 60_000);
        try (GatewayCallGovernor.CallLease occupied = governor.tryAcquire(1L, "M")) {
            assertEquals(1, governor.inFlight());
            BusinessException busy = assertThrows(BusinessException.class,
                    () -> governor.tryAcquire(1L, "M"));
            assertEquals(ErrorCode.GATEWAY_BUSY, busy.code,
                    "429 semantics: a dedicated, retryable code, not a generic provider error");
            assertTrue(busy.getMessage().contains("稍后重试"), "the refusal must say retry, not dead-end");
            assertTrue(busy.getMessage().contains("没有产生任何消耗"), "a refused call spent nothing");
        }
        assertEquals(0, governor.inFlight(), "close() must hand the slot back");
        assertDoesNotThrow(() -> governor.tryAcquire(1L, "M").close(), "retry after release succeeds");
    }

    @Test
    void leaseCloseIsIdempotentSoAFinallyDoubleCloseCannotInflateTheGate() {
        GatewayCallGovernor governor = governor(1, 60_000);
        GatewayCallGovernor.CallLease lease = governor.tryAcquire(1L, "M");
        lease.close();
        lease.close();
        assertEquals(0, governor.inFlight(), "a double release must not manufacture extra permits");
    }

    @Test
    void deadlineInterruptsSlowProviderWorkFailClosed() throws Exception {
        GatewayCallGovernor governor = governor(4, 120);
        AtomicBoolean interrupted = new AtomicBoolean(false);
        long deadlineAt = governor.deadlineAt();

        GatewayCallGovernor.GatewayDeadlineExceededException exceeded = assertThrows(
                GatewayCallGovernor.GatewayDeadlineExceededException.class,
                () -> governor.runWithDeadline(deadlineAt, "SLOW_MODULE", () -> {
                    try {
                        Thread.sleep(10_000);
                    } catch (InterruptedException e) {
                        interrupted.set(true);
                        return "interrupted";
                    }
                    return "late";
                }));
        assertTrue(exceeded.getMessage()
                        .contains(GatewayCallGovernor.GatewayDeadlineExceededException.DETAIL_MARKER),
                "the failure detail must be distinguishable as a deadline kill: " + exceeded.getMessage());
        // The worker thread must actually observe the interrupt (cancelled future), promptly.
        long stopBy = System.currentTimeMillis() + 5_000;
        while (!interrupted.get() && System.currentTimeMillis() < stopBy) {
            Thread.sleep(20);
        }
        assertTrue(interrupted.get(), "the slow call must be interrupted, not left running headless");
    }

    @Test
    void alreadyExpiredBudgetFailsClosedWithoutRunningTheProvider() {
        GatewayCallGovernor governor = governor(4, 60_000);
        AtomicBoolean ran = new AtomicBoolean(false);
        assertThrows(GatewayCallGovernor.GatewayDeadlineExceededException.class,
                () -> governor.runWithDeadline(System.currentTimeMillis() - 1, "M", () -> {
                    ran.set(true);
                    return "never";
                }));
        assertTrue(!ran.get(), "no budget means no provider egress at all");
    }

    @Test
    void providerExceptionsPassThroughUnchanged() {
        GatewayCallGovernor governor = governor(4, 60_000);
        IllegalStateException rateLimited = assertThrows(IllegalStateException.class,
                () -> governor.runWithDeadline(governor.deadlineAt(), "M", () -> {
                    throw new IllegalStateException("429 Too Many Requests");
                }));
        assertEquals("429 Too Many Requests", rateLimited.getMessage(),
                "the gate must not mask, wrap or flatten the provider's own failure");
    }

    @Test
    void inBudgetCallsReturnNormallyUnderConcurrency() throws Exception {
        GatewayCallGovernor governor = governor(2, 60_000);
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = pool.submit(() -> governor.runWithDeadline(
                    governor.deadlineAt(), "M", awaitAndReturn(entered, release)));
            Future<String> second = pool.submit(() -> governor.runWithDeadline(
                    governor.deadlineAt(), "M", awaitAndReturn(entered, release)));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            release.countDown();
            assertEquals("ok", first.get(5, TimeUnit.SECONDS));
            assertEquals("ok", second.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private static java.util.function.Supplier<String> awaitAndReturn(
            CountDownLatch entered, CountDownLatch release) {
        return () -> {
            try {
                entered.countDown();
                release.await();
                return "ok";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "interrupted";
            }
        };
    }
}
