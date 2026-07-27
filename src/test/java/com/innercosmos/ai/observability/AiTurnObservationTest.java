package com.innercosmos.ai.observability;

import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;

import static io.micrometer.observation.tck.TestObservationRegistryAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A6: the AI turn observation (→ span with a tracer) must be emitted with the declared bounded,
 * low-cardinality attributes and must stay privacy-safe — no attribute may carry a user id, message,
 * or content, and the latency is a coarse bucket, never a raw millisecond value.
 */
class AiTurnObservationTest {

    @Test
    void emitsAuroraTurnObservationWithBoundedLowCardinalityTags() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        AiTurnObservation observation = new AiTurnObservation(registry);

        observation.record("chat", "dual-kernel.v1", "glm", "DAILY_TALK", false, true, 1500);

        assertThat(registry)
                .hasObservationWithNameEqualTo("aurora.turn")
                .that()
                .hasBeenStarted()
                .hasBeenStopped()
                .hasLowCardinalityKeyValue("route", "chat")
                .hasLowCardinalityKeyValue("runtime", "dual-kernel.v1")
                .hasLowCardinalityKeyValue("provider", "glm")
                .hasLowCardinalityKeyValue("mode", "DAILY_TALK")
                .hasLowCardinalityKeyValue("fallback", "false")
                .hasLowCardinalityKeyValue("memory_referenced", "true")
                .hasLowCardinalityKeyValue("duration_bucket", "1-3s");
    }

    @Test
    void normalisesNullInputsAndNeverEmitsSensitiveKeys() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        AiTurnObservation observation = new AiTurnObservation(registry);

        observation.record("chat", null, null, null, true, false, -1);

        assertThat(registry)
                .hasObservationWithNameEqualTo("aurora.turn")
                .that()
                // null/blank inputs are normalised, not dropped.
                .hasLowCardinalityKeyValue("provider", "unknown")
                .hasLowCardinalityKeyValue("runtime", "unknown")
                .hasLowCardinalityKeyValue("duration_bucket", "unknown")
                .hasLowCardinalityKeyValue("fallback", "true")
                // privacy-safe: never a user id / message / content attribute.
                .doesNotHaveLowCardinalityKeyValueWithKey("userId")
                .doesNotHaveLowCardinalityKeyValueWithKey("message")
                .doesNotHaveLowCardinalityKeyValueWithKey("content")
                .doesNotHaveLowCardinalityKeyValueWithKey("duration_ms");
    }

    @Test
    void bucketsLatencyCoarsely() {
        assertEquals("<1s", AiTurnObservation.durationBucket(999));
        assertEquals("1-3s", AiTurnObservation.durationBucket(1000));
        assertEquals("3-10s", AiTurnObservation.durationBucket(3000));
        assertEquals(">10s", AiTurnObservation.durationBucket(10000));
        assertEquals("unknown", AiTurnObservation.durationBucket(-5));
    }

    @Test
    void providerObservationMeasuresTheActualScopedCallWithoutSensitiveTags() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        AiTurnObservation observation = new AiTurnObservation(registry);

        var provider = observation.startProvider("deepseek", "COMPANION");
        try (var ignored = provider.openScope()) {
            // Represents the provider/runtime call owned by AuroraAgentServiceImpl.
        } finally {
            provider.stop();
        }

        assertThat(registry)
                .hasObservationWithNameEqualTo("inner.cosmos.ai.provider")
                .that()
                .hasBeenStarted()
                .hasBeenStopped()
                .hasLowCardinalityKeyValue("provider", "deepseek")
                .hasLowCardinalityKeyValue("mode", "COMPANION")
                .doesNotHaveLowCardinalityKeyValueWithKey("userId")
                .doesNotHaveLowCardinalityKeyValueWithKey("message")
                .doesNotHaveLowCardinalityKeyValueWithKey("prompt");
    }

    @Test
    void turnLivesAroundProviderAndBothRemainChildrenOfTheIncomingHttpObservation() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        AiTurnObservation observations = new AiTurnObservation(registry);
        Observation http = Observation.start("http.server.requests", registry);
        try (var httpScope = http.openScope()) {
            Observation turn = observations.startTurn();
            try (var turnScope = turn.openScope()) {
                Observation provider = observations.startProvider("gemini", "COMPANION");
                try (var providerScope = provider.openScope()) {
                    // The real provider call executes here.
                } finally {
                    provider.stop();
                }
                observations.record("chat", "dual-kernel.v1", "gemini", "COMPANION",
                        false, true, 1200);
            } finally {
                turn.stop();
            }
        } finally {
            http.stop();
        }

        assertThat(registry).hasHandledContextsThatSatisfy(contexts -> {
            var httpContext = contexts.stream()
                    .filter(context -> "http.server.requests".equals(context.getName()))
                    .findFirst().orElseThrow();
            var turnContext = contexts.stream()
                    .filter(context -> "aurora.turn".equals(context.getName()))
                    .findFirst().orElseThrow();
            var providerContext = contexts.stream()
                    .filter(context -> "inner.cosmos.ai.provider".equals(context.getName()))
                    .findFirst().orElseThrow();

            assertNotNull(turnContext.getParentObservation());
            assertEquals(httpContext, turnContext.getParentObservation().getContextView());
            assertNotNull(providerContext.getParentObservation());
            assertEquals(turnContext, providerContext.getParentObservation().getContextView());
        });
    }
}
