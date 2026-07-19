package com.innercosmos.ai.observability;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A6: the AI turn metrics must record a counter + timer with the declared bounded tags, and must be
 * privacy-safe — no tag may carry message text, a user id, memory content or any high-cardinality
 * per-user value.
 */
class AiTurnMetricsTest {

    private static final Set<String> ALLOWED_TAG_KEYS =
            Set.of("route", "runtime", "provider", "mode", "fallback", "memory_referenced");

    @Test
    void recordsCounterAndTimerWithDeclaredBoundedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiTurnMetrics metrics = new AiTurnMetrics(registry);

        metrics.recordTurn("chat", "dual-kernel.v1", "glm", "DAILY_TALK", false, true, 1234);

        var counter = registry.find("aurora.turn.count").counter();
        var timer = registry.find("aurora.turn.latency").timer();
        assertEquals(1.0, counter.count());
        assertEquals(1L, timer.count());
        assertTrue(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) >= 1234.0);

        assertEquals("dual-kernel.v1", tag(counter, "runtime"));
        assertEquals("glm", tag(counter, "provider"));
        assertEquals("false", tag(counter, "fallback"));
        assertEquals("true", tag(counter, "memory_referenced"));
    }

    @Test
    void neverEmitsHighCardinalityOrSensitiveTagKeys() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiTurnMetrics metrics = new AiTurnMetrics(registry);

        // Even if a caller passed something odd, the meter's tag KEYS are a fixed allowlist.
        metrics.recordTurn("chat", null, null, null, true, false, -5);

        registry.getMeters().forEach(meter -> {
            Set<String> keys = meter.getId().getTags().stream().map(Tag::getKey).collect(Collectors.toSet());
            assertTrue(ALLOWED_TAG_KEYS.containsAll(keys),
                    "unexpected metric tag key(s): " + keys);
            assertFalse(keys.contains("userId") || keys.contains("message") || keys.contains("content"),
                    "sensitive tag key leaked");
        });
        // null/blank inputs are normalised, not dropped.
        assertEquals("unknown", tag(registry.find("aurora.turn.count").counter(), "provider"));
        assertEquals("true", tag(registry.find("aurora.turn.count").counter(), "fallback"));
    }

    private static String tag(Meter meter, String key) {
        return meter.getId().getTag(key);
    }
}
