package com.innercosmos.event.reliable;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-39 outbox reliability, H2-portable slice: the writer↔handler registry (an unhandled
 * type retried to DEAD is a silent drop — retraction notices must never die in a queue),
 * duplicate handler registration rejected at construction, and dedup-key append
 * idempotency. The claim/DEAD/replay lifecycle exercises PostgreSQL-native SKIP LOCKED
 * claiming and lives in PostgresOutboxReliabilityTest (Docker-gated) — an H2 twin of SKIP
 * LOCKED would test a weaker implementation and prove nothing about production.
 */
@SpringBootTest(properties = {
        "inner-cosmos.events.outbox.enabled=true",
        "inner-cosmos.events.outbox.smoke-probe-enabled=true",
        "spring.task.scheduling.enabled=false"
})
class OutboxReliabilityContractTest {

    @Autowired JdbcOutboxRepository repository;
    @Autowired List<OutboxEventHandler> handlers;
    @Autowired DataRetractedProjectionHandler retractionHandler;

    @Test
    void everyEmittedEventTypeHasARegisteredHandler() {
        Set<String> registered = handlers.stream()
                .map(OutboxEventHandler::eventType).collect(Collectors.toSet());
        // The registry: every event type any writer appends, enumerated at its writer.
        List<String> emitted = List.of(
                DialogFinishedOutboxWriter.EVENT_TYPE,
                DataRetractedOutboxWriter.EVENT_TYPE,
                OutboxSmokeProbeHandler.EVENT_TYPE);
        for (String eventType : emitted) {
            assertTrue(registered.contains(eventType),
                    "no handler for " + eventType + " — unhandled events retry to DEAD");
        }
        // Handler identity fields are real, not blank placeholders.
        for (OutboxEventHandler handler : handlers) {
            assertFalse(handler.eventType().isBlank());
            assertFalse(handler.consumerName().isBlank());
        }
    }

    @Test
    void duplicateHandlerRegistrationFailsAtConstructionNotAtRuntime() {
        assertThrows(IllegalStateException.class,
                () -> new JdbcOutboxWorker(repository, List.of(retractionHandler, retractionHandler),
                        null, null));
    }

    @Test
    void appendIsIdempotentPerDedupKey() {
        String dedupKey = "test-dedup:" + UUID.randomUUID();
        assertTrue(append(dedupKey, "system.outbox-smoke-probe.v1", "{}"));
        assertFalse(append(dedupKey, "system.outbox-smoke-probe.v1", "{}"),
                "a duplicate append with the same dedup key must be a no-op");
    }

    private boolean append(String dedupKey, String eventType, String payload) {
        return repository.append(UUID.randomUUID(), dedupKey, "test", "test",
                eventType, 1, payload, null);
    }

}
