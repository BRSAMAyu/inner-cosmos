package com.innercosmos.event.reliable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.event.DataRetractedEvent;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A5 durable/replayable delivery of data-retraction facts. Mirrors {@link DialogFinishedOutboxWriter}:
 * on BEFORE_COMMIT of the owner data-rights transaction, append a {@code data.retracted.v1} row to the
 * transactional JDBC outbox, so downstream caches/projections/consumers can invalidate reliably (with
 * inbox dedup + retry via {@link JdbcOutboxWorker}) instead of relying only on the synchronous
 * in-transaction fan-out. Gated on {@code inner-cosmos.events.outbox.enabled=true}; payload is
 * sensitive-free (the same fields as the audit receipt).
 */
@Component
@ConditionalOnProperty(name = "inner-cosmos.events.outbox.enabled", havingValue = "true")
public class DataRetractedOutboxWriter {
    public static final String EVENT_TYPE = "data.retracted.v1";
    public static final int SCHEMA_VERSION = 1;

    private final JdbcOutboxRepository repository;
    private final ObjectMapper objectMapper;

    public DataRetractedOutboxWriter(JdbcOutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onDataRetracted(DataRetractedEvent event) {
        try {
            // LinkedHashMap (not Map.of) to keep a stable field order and allow null-free explicit keys.
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("receiptId", event.receiptId);
            body.put("userId", event.userId);
            body.put("subjectType", event.subjectType);
            body.put("subjectId", event.subjectId);
            body.put("derivativeType", event.derivativeType);
            body.put("action", event.action);
            body.put("affectedCount", event.affectedCount);
            repository.append(
                    UUID.randomUUID(),
                    "data-retraction:" + event.receiptId + ":v1",
                    "data-retraction",
                    String.valueOf(event.receiptId),
                    EVENT_TYPE,
                    SCHEMA_VERSION,
                    objectMapper.writeValueAsString(body),
                    MDC.get("traceId"));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize data-retracted outbox payload", e);
        }
    }
}
