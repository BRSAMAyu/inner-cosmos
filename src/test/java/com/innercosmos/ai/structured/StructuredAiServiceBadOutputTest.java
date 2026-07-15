package com.innercosmos.ai.structured;

import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.config.LlmConfig;
import com.innercosmos.service.ABTestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Map;

/**
 * IC-CORE-002: StructuredAiService bad-output paths must increment badOutputCounter
 * and always return the fallback (never throw, never return null).
 */
@ExtendWith(MockitoExtension.class)
class StructuredAiServiceBadOutputTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private ABTestService abTestService;

    @Mock
    private LlmConfig llmConfig;

    private StructuredAiService service;

    /** Simple result type for tests. */
    public static class ScoreResult {
        public int score;
    }

    @BeforeEach
    void setUp() {
        service = new StructuredAiService(llmClient, abTestService, llmConfig);
        // Reset the static counter before each test
        StructuredAiService.badOutputCounter.set(0);

        // Default stubs used by most tests
        when(abTestService.assignGroup(any(), any())).thenReturn("MOCK");
        when(llmConfig.isProdMode()).thenReturn(false);
        doNothing().when(abTestService).recordMetrics(any(), any(), any(), anyDouble(), anyBoolean(), anyBoolean());
    }

    // -----------------------------------------------------------------------
    // Test 1: both parse attempts fail → counter incremented, fallback returned
    // -----------------------------------------------------------------------
    @Test
    void malformedJson_bothAttemptsFail_counterIncrementedAndFallbackReturned() {
        // Both raw and repaired responses are unparseable
        when(llmClient.chat(any(LlmRequest.class))).thenReturn("not json at all");

        ScoreResult fallback = new ScoreResult();
        fallback.score = -1;

        ScoreResult result = service.call(1L, "TEST_MODULE", "instruction", null,
                ScoreResult.class, () -> fallback);

        assertNotNull(result, "Should not return null — fallback must be returned");
        assertEquals(-1, result.score, "Should return the explicit fallback value");
        assertTrue(StructuredAiService.badOutputCounter.get() > 0,
                "badOutputCounter should be > 0 after bad output");
    }

    // -----------------------------------------------------------------------
    // Test 2: LLM returns null → counter incremented, fallback returned
    // -----------------------------------------------------------------------
    @Test
    void nullLlmResponse_counterIncrementedAndFallbackReturned() {
        when(llmClient.chat(any(LlmRequest.class))).thenReturn(null);

        ScoreResult fallback = new ScoreResult();
        fallback.score = -2;

        ScoreResult result = service.call(1L, "TEST_MODULE", "instruction", null,
                ScoreResult.class, () -> fallback);

        assertNotNull(result, "Should not return null — fallback must be returned");
        assertEquals(-2, result.score, "Should return the explicit fallback value");
        assertTrue(StructuredAiService.badOutputCounter.get() > 0,
                "badOutputCounter should be > 0 after null LLM response");
    }

    // -----------------------------------------------------------------------
    // Test 3: LLM returns blank string → counter incremented, fallback returned
    // -----------------------------------------------------------------------
    @Test
    void blankLlmResponse_counterIncrementedAndFallbackReturned() {
        when(llmClient.chat(any(LlmRequest.class))).thenReturn("   ");

        ScoreResult fallback = new ScoreResult();
        fallback.score = -3;

        ScoreResult result = service.call(1L, "TEST_MODULE", "instruction", null,
                ScoreResult.class, () -> fallback);

        assertNotNull(result, "Should not return null — fallback must be returned");
        assertEquals(-3, result.score, "Should return the explicit fallback value");
        assertTrue(StructuredAiService.badOutputCounter.get() > 0,
                "badOutputCounter should be > 0 after blank LLM response");
    }

    // -----------------------------------------------------------------------
    // Test 4: valid JSON → counter stays at 0 (no bad output logged)
    // -----------------------------------------------------------------------
    @Test
    void validJson_counterRemainsZero() {
        when(llmClient.chat(any(LlmRequest.class))).thenReturn("{\"score\": 5}");

        ScoreResult fallback = new ScoreResult();
        fallback.score = -99;

        ScoreResult result = service.call(1L, "TEST_MODULE", "instruction", null,
                ScoreResult.class, () -> fallback);

        assertNotNull(result, "Should not return null for valid JSON");
        assertEquals(5, result.score, "Should parse score from valid JSON");
        assertEquals(0, StructuredAiService.badOutputCounter.get(),
                "badOutputCounter must be 0 when LLM returns valid JSON");
    }

    @Test
    void sendsAuroraBoundaryAsProviderSystemRoleContract() {
        when(llmClient.chat(any(LlmRequest.class))).thenReturn("{\"score\": 5}");

        service.call(1L, "AURORA_PLAN_TEST", "plan", Map.of(
                        "auroraSystemPrompt", "AURORA_IDENTITY_AND_SAFETY",
                        "userMessage", "dynamic user text"),
                ScoreResult.class, ScoreResult::new);

        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmClient).chat(captor.capture());
        LlmRequest request = captor.getValue();
        assertTrue(request.systemPrompt.contains("AURORA_IDENTITY_AND_SAFETY"));
        assertTrue(request.systemPrompt.contains("structured reasoning worker"));
        assertFalse(request.prompt.contains("AURORA_IDENTITY_AND_SAFETY"));
        assertTrue(request.prompt.contains("dynamic user text"));
    }
}
