package com.innercosmos.event.reliable;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxSmokeProbeHandlerTest {
    private final OutboxSmokeProbeHandler handler = new OutboxSmokeProbeHandler(new ObjectMapper());

    @Test
    void acceptsAWellFormedSyntheticProbeWithoutDomainSideEffects() {
        assertThatCode(() -> handler.handle(event("{\"probeId\":\"image-smoke-1\"}")))
                .doesNotThrowAnyException();
        assertThat(handler.eventType()).isEqualTo(OutboxSmokeProbeHandler.EVENT_TYPE);
        assertThat(handler.consumerName()).isEqualTo("outbox-smoke-probe-v1");
    }

    @Test
    void rejectsMissingBlankAndMalformedProbeIdentifiers() {
        assertThatThrownBy(() -> handler.handle(event("{}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("probeId");
        assertThatThrownBy(() -> handler.handle(event("{\"probeId\":\"  \"}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("probeId");
        assertThatThrownBy(() -> handler.handle(event("not-json")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid");
    }

    private OutboxEvent event(String payload) {
        return new OutboxEvent(1L, UUID.randomUUID(), "image-smoke-1",
                "system", "image-smoke-1", OutboxSmokeProbeHandler.EVENT_TYPE, 1,
                payload, "trace", 0, "worker", LocalDateTime.now().plusSeconds(30));
    }
}
