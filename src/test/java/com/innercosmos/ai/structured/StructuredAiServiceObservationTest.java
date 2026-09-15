package com.innercosmos.ai.structured;

import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.ai.gateway.GatewayCallGovernor;
import com.innercosmos.ai.structured.StructuredAiService.CallOutcome;
import com.innercosmos.ai.structured.StructuredAiService.CallStatus;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.config.LlmConfig;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.service.ABTestService;
import io.micrometer.observation.Observation;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static io.micrometer.observation.tck.TestObservationRegistryAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * CP-40 §2-19: the {@code callObserved} throat must emit one
 * {@code inner.cosmos.ai.structured.call} observation (→ span with the OTel bridge) per
 * structured AI call, with the declared bounded attributes and a truthful outcome on EVERY
 * exit path — success, fallback and the pre-flight refusals that propagate.
 *
 * <p>Verification boundary (honest constraint): assertions capture the observation through
 * micrometer's in-memory {@link TestObservationRegistry}. That proves the span exists with the
 * right name/attributes/outcome at the Micrometer layer that micrometer-tracing-bridge-otel
 * converts to OTel spans; it does NOT prove export to a real OTLP collector, which needs a
 * deployed collector (opt-in via OTLP_TRACING_ENDPOINT/OTLP_TRACING_ENABLED in deployment).
 */
@ExtendWith(MockitoExtension.class)
class StructuredAiServiceObservationTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private ABTestService abTestService;

    @Mock
    private LlmConfig llmConfig;

    private TestObservationRegistry registry;
    private StructuredAiService service;

    public static class ScoreResult {
        public int score;
    }

    @BeforeEach
    void setUp() {
        registry = TestObservationRegistry.create();
        service = new StructuredAiService(llmClient, abTestService, llmConfig);
        org.springframework.test.util.ReflectionTestUtils.setField(
                service, "observationRegistry", registry);
        StructuredAiService.badOutputCounter.set(0);
        // REMOTE by default: the governed provider leg these spans describe.
        when(abTestService.assignGroup(any(), any())).thenReturn("REMOTE");
        when(llmConfig.isProdMode()).thenReturn(false);
        // Lenient: the pre-flight refusal test never reaches the metrics finally-block.
        lenient().doNothing().when(abTestService)
                .recordMetrics(any(), any(), any(), anyDouble(), anyBoolean(), anyBoolean());
    }

    private static ScoreResult fallback(int score) {
        ScoreResult fallback = new ScoreResult();
        fallback.score = score;
        return fallback;
    }

    @Test
    void successPathEmitsStructuredCallSpanWithBoundedRoutingAttributes() {
        when(llmClient.chat(any(LlmRequest.class))).thenReturn("{\"score\": 5}");

        CallOutcome<ScoreResult> outcome = service.callObserved(9L, "AURORA_SPEAKER_TALK", "instr",
                Map.of(), ScoreResult.class, () -> fallback(-1), null);

        assertEquals(CallStatus.SUCCESS, outcome.status());
        assertThat(registry)
                .hasObservationWithNameEqualTo(StructuredAiService.STRUCTURED_CALL_OBSERVATION)
                .that()
                .hasBeenStarted()
                .hasBeenStopped()
                .hasLowCardinalityKeyValue("module", "AURORA_SPEAKER_TALK")
                // no preferredProvider in the context → honest "unspecified", never a guess
                .hasLowCardinalityKeyValue("provider", "unspecified")
                .hasLowCardinalityKeyValue("leg", "REMOTE")
                .hasLowCardinalityKeyValue("outcome", "SUCCESS");
    }

    @Test
    void providerPreferenceAndMockLegAreNormalisedOntoTheSpan() {
        when(abTestService.assignGroup(any(), any())).thenReturn("MOCK");
        when(llmClient.chat(any(LlmRequest.class))).thenReturn("{\"score\": 5}");

        service.callObserved(9L, "MEMORY_EXTRACT", "instr",
                Map.of("preferredProvider", "GLM"), ScoreResult.class, () -> fallback(-1), null);

        assertThat(registry)
                .hasObservationWithNameEqualTo(StructuredAiService.STRUCTURED_CALL_OBSERVATION)
                .that()
                .hasBeenStopped()
                .hasLowCardinalityKeyValue("module", "MEMORY_EXTRACT")
                // caller preference normalised to a bounded lowercase token
                .hasLowCardinalityKeyValue("provider", "glm")
                .hasLowCardinalityKeyValue("leg", "MOCK")
                .hasLowCardinalityKeyValue("outcome", "SUCCESS");
    }

    @Test
    void providerFailureEmitsFailedOutcomeWithBoundedErrorType() {
        when(llmClient.chat(any(LlmRequest.class)))
                .thenThrow(new IllegalStateException("429 Too Many Requests: rate limited"));

        CallOutcome<ScoreResult> outcome = service.callObserved(9L, "AURORA_SPEAKER_TALK", "instr",
                Map.of(), ScoreResult.class, () -> fallback(-1), null);

        assertEquals(CallStatus.FAILED, outcome.status());
        assertThat(registry)
                .hasObservationWithNameEqualTo(StructuredAiService.STRUCTURED_CALL_OBSERVATION)
                .that()
                .hasBeenStarted()
                .hasBeenStopped()
                .hasLowCardinalityKeyValue("module", "AURORA_SPEAKER_TALK")
                .hasLowCardinalityKeyValue("outcome", "FAILED")
                // bounded class token, never the exception message (it may echo provider bodies)
                .hasLowCardinalityKeyValue("error.type", "IllegalStateException");
    }

    @Test
    void gatewayBusyRefusalStillClosesTheSpanWithRefusedOutcome() {
        GatewayCallGovernor governor = new GatewayCallGovernor();
        org.springframework.test.util.ReflectionTestUtils.setField(governor, "maxConcurrency", 1);
        org.springframework.test.util.ReflectionTestUtils.setField(governor, "callDeadlineMs", 60_000);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "gatewayGovernor", governor);

        try (GatewayCallGovernor.CallLease occupied = governor.tryAcquire(1L, "REMOTE_MODULE")) {
            BusinessException busy = assertThrows(BusinessException.class, () ->
                    service.callObserved(9L, "AURORA_SPEAKER_TALK", "instr", Map.of(),
                            ScoreResult.class, () -> fallback(-1), null));
            assertEquals(ErrorCode.GATEWAY_BUSY, busy.code);
        }

        assertThat(registry)
                .hasObservationWithNameEqualTo(StructuredAiService.STRUCTURED_CALL_OBSERVATION)
                .that()
                .hasBeenStarted()
                .hasBeenStopped()
                .hasLowCardinalityKeyValue("outcome", "REFUSED")
                .hasLowCardinalityKeyValue("refusal", "GATEWAY_BUSY");
    }

    @Test
    void spanNeverCarriesUserIdInstructionPromptOrContextContent() {
        // The marker travels ONLY in the instruction, the context map and the provider error
        // surface — if any span attribute value contains it, P0 content leaked into tracing.
        String marker = "SECRET-CONTEXT-MARKER-7f3a";
        when(llmClient.chat(any(LlmRequest.class)))
                .thenThrow(new IllegalStateException("provider echoed " + marker));

        service.callObserved(42L, "AURORA_SPEAKER_TALK", "instruction " + marker,
                Map.of("userMessage", "user says " + marker),
                ScoreResult.class, () -> fallback(-1), null);

        assertThat(registry)
                .hasObservationWithNameEqualTo(StructuredAiService.STRUCTURED_CALL_OBSERVATION)
                .that()
                .hasBeenStopped()
                // repo AI tracing discipline: no user identifier on spans at all — not even a
                // presence boolean (documented on the chokepoint).
                .doesNotHaveLowCardinalityKeyValueWithKey("userId")
                .doesNotHaveLowCardinalityKeyValueWithKey("user_id")
                .doesNotHaveLowCardinalityKeyValueWithKey("user_present")
                .doesNotHaveLowCardinalityKeyValueWithKey("instruction")
                .doesNotHaveLowCardinalityKeyValueWithKey("prompt")
                .doesNotHaveLowCardinalityKeyValueWithKey("message")
                .doesNotHaveLowCardinalityKeyValueWithKey("context");

        assertThat(registry).hasHandledContextsThatSatisfy(contexts ->
                contexts.forEach(context -> context.getAllKeyValues().forEach(keyValue -> {
                    assertTrue(!keyValue.getValue().contains(marker),
                            "span attribute " + keyValue.getKey() + " leaked P0 content: " + keyValue.getValue());
                })));
    }
}
