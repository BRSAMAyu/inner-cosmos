package com.innercosmos.ai.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Component;

/**
 * A6 privacy-safe AI tracing. Emits an {@code aurora.turn} Micrometer Observation per turn — which
 * becomes a real OpenTelemetry (or any bridged) <em>span</em> when a tracer is configured, and is a
 * cheap no-op when none is. Carries only bounded, low-cardinality, non-sensitive attributes (route,
 * kernel runtime, provider, mode, fallback, memory-referenced, and a coarse duration bucket) — never
 * message text, prompts, retrieval content, memory ids or the user id.
 *
 * <p>The observation is emitted at turn completion, paired with the {@link AiTurnMetrics} timer that
 * remains the authoritative latency signal; the span's value is trace correlation + the bounded
 * attributes, so the exact per-turn latency is exposed as a coarse {@code duration_bucket} tag rather
 * than a high-cardinality millisecond value.
 */
@Component
public class AiTurnObservation {
    public static final String NAME = "aurora.turn";

    private final ObservationRegistry registry;

    public AiTurnObservation(ObservationRegistry registry) {
        this.registry = registry;
    }

    /**
     * Starts the span that measures the actual provider/runtime call. The caller owns its scope
     * and must stop it in a finally block. Only bounded routing decisions are attached.
     */
    public Observation startProvider(String provider, String mode) {
        return Observation.createNotStarted("inner.cosmos.ai.provider", registry)
                .lowCardinalityKeyValue("provider", safe(provider))
                .lowCardinalityKeyValue("mode", safe(mode))
                .start();
    }

    public void record(String route, String runtime, String provider, String mode,
                       boolean fallbackUsed, boolean memoryReferenced, long durationMs) {
        Observation.createNotStarted(NAME, registry)
                .lowCardinalityKeyValue("route", safe(route))
                .lowCardinalityKeyValue("runtime", safe(runtime))
                .lowCardinalityKeyValue("provider", safe(provider))
                .lowCardinalityKeyValue("mode", safe(mode))
                .lowCardinalityKeyValue("fallback", Boolean.toString(fallbackUsed))
                .lowCardinalityKeyValue("memory_referenced", Boolean.toString(memoryReferenced))
                .lowCardinalityKeyValue("duration_bucket", durationBucket(durationMs))
                .observe(() -> { /* completion event; the timed work already ran */ });
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    /** Coarse, low-cardinality latency bucket — keeps the span tag set bounded. */
    static String durationBucket(long ms) {
        if (ms < 0) return "unknown";
        if (ms < 1000) return "<1s";
        if (ms < 3000) return "1-3s";
        if (ms < 10000) return "3-10s";
        return ">10s";
    }
}
