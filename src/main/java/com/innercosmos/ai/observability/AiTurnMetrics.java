package com.innercosmos.ai.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * A6 privacy-safe AI observability: one counter + one timer per Aurora turn, tagged only with
 * bounded, low-cardinality, non-sensitive dimensions (route, kernel runtime, provider, mode, and
 * whether a fallback ran / memory was referenced). It NEVER records message text, prompts, retrieval
 * content, memory ids or the user id — exactly the AI cost/quality signal the acceptance ledger's
 * OPS-OBSERVABILITY / A6 asks for, safe to expose over /actuator/prometheus.
 *
 * <p>Meters:
 * <ul>
 *   <li>{@code aurora.turn.count} — turns produced, by the tags below;</li>
 *   <li>{@code aurora.turn.latency} — end-to-end produce-reply latency, same tags.</li>
 * </ul>
 */
@Component
public class AiTurnMetrics {
    private final MeterRegistry registry;

    public AiTurnMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordTurn(String route, String runtime, String provider, String mode,
                           boolean fallbackUsed, boolean memoryReferenced, long durationMs) {
        Tags tags = Tags.of(
                "route", safe(route),
                "runtime", safe(runtime),
                "provider", safe(provider),
                "mode", safe(mode),
                "fallback", Boolean.toString(fallbackUsed),
                "memory_referenced", Boolean.toString(memoryReferenced));
        registry.counter("aurora.turn.count", tags).increment();
        registry.timer("aurora.turn.latency", tags).record(Duration.ofMillis(Math.max(0, durationMs)));
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
