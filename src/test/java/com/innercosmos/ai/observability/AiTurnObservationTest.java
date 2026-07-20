package com.innercosmos.ai.observability;

import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.Test;

import static io.micrometer.observation.tck.TestObservationRegistryAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
