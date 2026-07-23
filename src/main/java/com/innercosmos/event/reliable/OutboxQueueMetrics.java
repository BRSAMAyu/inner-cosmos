package com.innercosmos.event.reliable;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Low-cardinality business pressure metrics. KEDA scales workers from queue truth instead of
 * API CPU, while dashboards can distinguish growing lag from a dead-letter incident.
 */
@Component
@ConditionalOnProperty(name = "inner-cosmos.events.outbox.enabled", havingValue = "true")
public class OutboxQueueMetrics {

    public OutboxQueueMetrics(JdbcOutboxRepository repository, MeterRegistry registry) {
        Gauge.builder("inner.cosmos.outbox.ready", repository, JdbcOutboxRepository::readyCount)
                .description("Outbox events currently eligible for a worker lease")
                .strongReference(true)
                .register(registry);
        Gauge.builder("inner.cosmos.outbox.oldest.ready.age.seconds", repository,
                        JdbcOutboxRepository::oldestReadyAgeSeconds)
                .description("Age in seconds of the oldest event eligible for processing")
                .baseUnit("seconds")
                .strongReference(true)
                .register(registry);
        Gauge.builder("inner.cosmos.outbox.dead", repository, JdbcOutboxRepository::deadCount)
                .description("Outbox events exhausted into the dead-letter state")
                .strongReference(true)
                .register(registry);
    }
}
