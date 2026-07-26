package com.innercosmos.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SseConnectionMetricsFilterTest {
    @Test
    void classifiesOnlyBoundedSseRoutes() {
        assertEquals("aurora_live", SseConnectionMetricsFilter.route("/api/aurora/stream"));
        assertEquals("aurora_live", SseConnectionMetricsFilter.route("/api/v1/aurora/stream"));
        assertEquals("aurora_replay", SseConnectionMetricsFilter.route("/api/v1/aurora/turns/7/events"));
        assertEquals("proactive", SseConnectionMetricsFilter.route("/api/proactive/stream"));
        assertNull(SseConnectionMetricsFilter.route("/api/aurora/message"));
    }

    @Test
    void openAndCloseAreIdempotentAndNeverUseIdentityTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SseConnectionMetricsFilter filter = new SseConnectionMetricsFilter(registry);
        var sample = filter.open("aurora_live", "false");

        assertEquals(1.0, registry.get("inner.cosmos.sse.connections.active").gauge().value());
        sample.close("complete");
        sample.close("error");

        assertEquals(0.0, registry.get("inner.cosmos.sse.connections.active").gauge().value());
        assertEquals(1.0, registry.get("inner.cosmos.sse.connections.closed")
                .tag("outcome", "complete").counter().count());
    }
}
