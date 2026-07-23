package com.innercosmos.event.reliable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class OutboxQueueMetricsTest {

    @Test
    void exposesOnlyBoundedAggregateQueueSignals() {
        JdbcOutboxRepository repository = mock(JdbcOutboxRepository.class);
        when(repository.readyCount()).thenReturn(37L);
        when(repository.oldestReadyAgeSeconds()).thenReturn(42.5);
        when(repository.deadCount()).thenReturn(2L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new OutboxQueueMetrics(repository, registry);

        assertThat(registry.get("inner.cosmos.outbox.ready").gauge().value()).isEqualTo(37.0);
        assertThat(registry.get("inner.cosmos.outbox.oldest.ready.age.seconds").gauge().value())
                .isEqualTo(42.5);
        assertThat(registry.get("inner.cosmos.outbox.dead").gauge().value()).isEqualTo(2.0);
        registry.getMeters().forEach(meter -> assertThat(meter.getId().getTags()).isEmpty());
    }
}
