package com.innercosmos.ai.structured;

import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.ai.gateway.GatewayCallGovernor;
import com.innercosmos.ai.observability.ProviderSpendGuard;
import com.innercosmos.ai.structured.StructuredAiService.CallOutcome;
import com.innercosmos.ai.structured.StructuredAiService.CallStatus;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.config.LlmConfig;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.service.ABTestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CP-17 gateway governance negative tests at the {@code callObserved} throat: injected
 * provider faults (429, slow-beyond-deadline, mid-stream truncation) and a saturated
 * concurrency gate must all land fail-closed — the call is marked failed, the spend
 * budget records no success, the refusal is honestly retryable where transient, and no
 * path fabricates a Mock-flavoured success.
 */
@ExtendWith(MockitoExtension.class)
class StructuredAiServiceGatewayGovernanceTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private ABTestService abTestService;

    @Mock
    private LlmConfig llmConfig;

    private ProviderSpendGuard spendGuard;
    private StructuredAiService service;

    public static class ScoreResult {
        public int score;
    }

    @BeforeEach
    void setUp() {
        spendGuard = new ProviderSpendGuard();
        org.springframework.test.util.ReflectionTestUtils.setField(spendGuard, "enabled", true);
        org.springframework.test.util.ReflectionTestUtils.setField(spendGuard, "dailyCallBudget", 400L);
        org.springframework.test.util.ReflectionTestUtils.setField(spendGuard, "dailyTokenBudget", 400_000L);
        service = new StructuredAiService(llmClient, abTestService, llmConfig);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "spendGuard", spendGuard);
        StructuredAiService.badOutputCounter.set(0);
        // Every governed test is REMOTE-bound: these gates exist for real provider spend.
        when(abTestService.assignGroup(any(), any())).thenReturn("REMOTE");
        when(llmConfig.isProdMode()).thenReturn(false);
        doNothing().when(abTestService).recordMetrics(any(), any(), any(), anyDouble(), anyBoolean(), anyBoolean());
    }

    private void armGovernor(int maxConcurrency, long deadlineMs) {
        GatewayCallGovernor governor = new GatewayCallGovernor();
        org.springframework.test.util.ReflectionTestUtils.setField(governor, "maxConcurrency", maxConcurrency);
        org.springframework.test.util.ReflectionTestUtils.setField(governor, "callDeadlineMs", deadlineMs);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "gatewayGovernor", governor);
    }

    private static ScoreResult fallback(int score) {
        ScoreResult fallback = new ScoreResult();
        fallback.score = score;
        return fallback;
    }

    // ------------------------------------------------------------------
    // Fault 1: provider answers 429 — failure path, budget records no success.
    // ------------------------------------------------------------------

    @Test
    void provider429FailsClosedWithoutSuccessOrSpendRecord() {
        armGovernor(4, 60_000);
        when(llmClient.chat(any(LlmRequest.class)))
                .thenThrow(new IllegalStateException("429 Too Many Requests: rate limited"));

        CallOutcome<ScoreResult> outcome = service.callObserved(9L, "AURORA_SPEAKER_TALK", "instr",
                Map.of(), ScoreResult.class, () -> fallback(-1), null);

        assertEquals(CallStatus.FAILED, outcome.status(), "a 429 must surface as FAILED, never success");
        assertTrue(outcome.usedFallback());
        assertTrue(outcome.detail().contains("429"), "the refusal reason stays visible: " + outcome.detail());
        assertEquals(-1, outcome.value().score, "the deterministic fallback is returned, not a fake reply");
        assertEquals(0, spendGuard.recordedCallsToday(9L),
                "budget records no successful spend for a refused call");
        assertTrue(StructuredAiService.badOutputCounter.get() > 0);
        verify(abTestService).recordMetrics(eq(9L), eq("REMOTE"), eq("AURORA_SPEAKER_TALK"),
                anyDouble(), eq(false), eq(true));
    }

    // ------------------------------------------------------------------
    // Fault 2: provider slower than the per-call deadline — interrupted, fail-closed,
    // and the slot/lease is released so a retry can succeed.
    // ------------------------------------------------------------------

    @Test
    void slowProviderIsInterruptedByTheDeadlineAndTheRetrySlotIsFree() {
        armGovernor(4, 150);
        when(llmClient.chat(any(LlmRequest.class))).thenAnswer(invocation -> {
            Thread.sleep(5_000);
            return "{\"score\": 5}";
        });

        CallOutcome<ScoreResult> outcome = service.callObserved(9L, "AURORA_PLAN_TALK", "instr",
                Map.of(), ScoreResult.class, () -> fallback(-2), null);

        assertEquals(CallStatus.FAILED, outcome.status());
        assertTrue(outcome.detail().contains(
                        GatewayCallGovernor.GatewayDeadlineExceededException.DETAIL_MARKER),
                "the deadline kill must be distinguishable in the detail: " + outcome.detail());
        assertEquals(-2, outcome.value().score);
        assertEquals(0, spendGuard.recordedCallsToday(9L),
                "an interrupted call never reached completion, so no spend success is recorded");
        assertTrue(StructuredAiService.badOutputCounter.get() > 0);

        // The lease was released on the failure path: an immediate retry with a fast
        // provider now succeeds — the failure is transient/retryable by construction.
        when(llmClient.chat(any(LlmRequest.class))).thenReturn("{\"score\": 5}");
        CallOutcome<ScoreResult> retried = service.callObserved(9L, "AURORA_PLAN_TALK", "instr",
                Map.of(), ScoreResult.class, () -> fallback(-2), null);
        assertEquals(CallStatus.SUCCESS, retried.status(), "slot must be free again after the deadline kill");
        assertEquals(5, retried.value().score);
    }

    // ------------------------------------------------------------------
    // Fault 3: stream interrupted mid-generation — the provider connection dies and
    // chat returns truncated output. Truthful invalid-JSON fallback, never a fake success.
    // ------------------------------------------------------------------

    @Test
    void midStreamTruncationYieldsTruthfulInvalidJsonFallbackNotFakeSuccess() {
        armGovernor(4, 60_000);
        when(llmClient.chat(any(LlmRequest.class))).thenReturn("{\"score\": 5");

        CallOutcome<ScoreResult> outcome = service.callObserved(9L, "AURORA_CRITIC_TALK", "instr",
                Map.of(), ScoreResult.class, () -> fallback(-3), null);

        assertEquals(CallStatus.FALLBACK_INVALID_JSON, outcome.status(),
                "a truncated stream must land in the invalid-JSON fallback bucket");
        assertTrue(outcome.usedFallback(), "truncated output is never presented as a real reply");
        assertEquals(-3, outcome.value().score);
        verify(llmClient, times(2)).chat(any(LlmRequest.class));
        // the repair retry also received truncated output — no rescue, no fake success
        assertEquals("invalid_json_after_repair", outcome.detail());
        assertTrue(StructuredAiService.badOutputCounter.get() > 0);
        verify(abTestService).recordMetrics(eq(9L), eq("REMOTE"), eq("AURORA_CRITIC_TALK"),
                anyDouble(), eq(false), eq(true));
    }

    // ------------------------------------------------------------------
    // Fault 4: concurrency gate saturated — 429-semantic refusal (GATEWAY_BUSY)
    // propagates instead of queueing or flattening into a fallback.
    // ------------------------------------------------------------------

    @Test
    void saturatedConcurrencyGateRefusesFastWithGatewayBusyAndRecovers() throws Exception {
        armGovernor(1, 60_000);
        CountDownLatch providerEntered = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        when(llmClient.chat(any(LlmRequest.class))).thenAnswer(invocation -> {
            providerEntered.countDown();
            releaseProvider.await();
            return "{\"score\": 5}";
        });
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<CallOutcome<ScoreResult>> inFlight = pool.submit(() ->
                    service.callObserved(9L, "AURORA_SPEAKER_TALK", "instr",
                            Map.of(), ScoreResult.class, () -> fallback(-4), null));
            assertTrue(providerEntered.await(5, TimeUnit.SECONDS),
                    "first call must reach the provider before the gate can be proven saturated");

            AtomicBoolean fallbackDemanded = new AtomicBoolean(false);
            BusinessException busy = assertThrows(BusinessException.class, () ->
                    service.callObserved(9L, "AURORA_SPEAKER_TALK", "instr", Map.of(),
                            ScoreResult.class, () -> {
                                fallbackDemanded.set(true);
                                return fallback(-4);
                            }, null));
            assertEquals(ErrorCode.GATEWAY_BUSY, busy.code,
                    "a saturated gate is a 429-semantic, retryable refusal");
            assertTrue(busy.getMessage().contains("稍后重试"));
            assertTrue(!fallbackDemanded.get(),
                    "the busy refusal must not silently consume the deterministic fallback");
            assertEquals(0, spendGuard.recordedCallsToday(9L),
                    "the refused call never reached the provider, so nothing was spent");

            releaseProvider.countDown();
            assertEquals(CallStatus.SUCCESS, inFlight.get(5, TimeUnit.SECONDS).status());

            // Retryable semantics: with the slot released the same request succeeds.
            CallOutcome<ScoreResult> retried = service.callObserved(9L, "AURORA_SPEAKER_TALK", "instr",
                    Map.of(), ScoreResult.class, () -> fallback(-4), null);
            assertEquals(CallStatus.SUCCESS, retried.status());
        } finally {
            releaseProvider.countDown();
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------
    // Fault 2b: the JSON-repair retry shares ONE deadline budget with the first
    // attempt — a slow first response plus a slow retry is killed by the same clock.
    // ------------------------------------------------------------------

    @Test
    void repairRetrySharesTheSameDeadlineBudget() {
        armGovernor(4, 1_500);
        when(llmClient.chat(any(LlmRequest.class)))
                .thenAnswer(invocation -> {
                    Thread.sleep(1_200);
                    return "not json at all"; // first attempt in budget, forces the repair retry
                })
                .thenAnswer(invocation -> {
                    Thread.sleep(10_000);
                    return "{\"score\": 5}"; // would parse, but the shared budget is nearly spent
                });

        CallOutcome<ScoreResult> outcome = service.callObserved(9L, "AURORA_PLAN_TALK", "instr",
                Map.of(), ScoreResult.class, () -> fallback(-5), null);

        verify(llmClient, times(2)).chat(any(LlmRequest.class));
        assertEquals(CallStatus.FAILED, outcome.status(),
                "the retry must be killed by the shared per-call deadline, not run budgetless");
        assertTrue(outcome.detail().contains(
                GatewayCallGovernor.GatewayDeadlineExceededException.DETAIL_MARKER));
        assertEquals(-5, outcome.value().score);
    }

    // ------------------------------------------------------------------
    // No fault, but a crucial regression guard: MOCK/experiment legs are untouched by
    // the gateway gates (the classroom demo and A/B tests keep working while the gate
    // is fully saturated for REMOTE calls).
    // ------------------------------------------------------------------

    @Test
    void mockBucketBypassesTheGatewayGatesEntirely() throws Exception {
        when(abTestService.assignGroup(any(), any())).thenReturn("MOCK");
        armGovernor(1, 60_000);
        when(llmClient.chat(any(LlmRequest.class))).thenReturn("{\"score\": 5}");

        try (GatewayCallGovernor.CallLease occupied = ((GatewayCallGovernor)
                org.springframework.test.util.ReflectionTestUtils.getField(service, "gatewayGovernor"))
                .tryAcquire(1L, "REMOTE_MODULE")) {
            CallOutcome<ScoreResult> outcome = service.callObserved(9L, "MEMORY_EXTRACT", "instr",
                    Map.of(), ScoreResult.class, () -> fallback(-6), null);
            assertEquals(CallStatus.SUCCESS, outcome.status(),
                    "a local mock leg carries no provider contention and must bypass the gate");
            assertEquals(5, outcome.value().score);
            verify(abTestService).recordMetrics(eq(9L), eq("MOCK"), eq("MEMORY_EXTRACT"),
                    anyDouble(), eq(true), eq(false));
        }
    }
}
