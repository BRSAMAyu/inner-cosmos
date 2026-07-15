package com.innercosmos.event.reliable;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "inner-cosmos.events.outbox.enabled", havingValue = "true")
public class JdbcOutboxRepository {
    private static final int MAX_ERROR_LENGTH = 500;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public JdbcOutboxRepository(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public boolean append(UUID eventId, String dedupKey, String aggregateType, String aggregateId,
                          String eventType, int schemaVersion, String payload, String traceId) {
        int inserted = jdbc.update("""
                INSERT INTO tb_outbox_event
                    (event_id, dedup_key, aggregate_type, aggregate_id, event_type,
                     schema_version, payload, trace_id, status, available_at)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, 'PENDING', CURRENT_TIMESTAMP)
                ON CONFLICT (dedup_key) DO NOTHING
                """, eventId, dedupKey, aggregateType, aggregateId, eventType,
                schemaVersion, payload, traceId);
        return inserted == 1;
    }

    public List<OutboxEvent> claim(String workerId, int batchSize, Duration lease) {
        return transactions.execute(status -> jdbc.query("""
                WITH candidates AS (
                    SELECT id
                    FROM tb_outbox_event
                    WHERE (status IN ('PENDING', 'RETRY') AND available_at <= CURRENT_TIMESTAMP)
                       OR (status = 'PROCESSING' AND locked_until < CURRENT_TIMESTAMP)
                    ORDER BY id
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE tb_outbox_event event
                SET status = 'PROCESSING',
                    locked_by = ?,
                    locked_until = CURRENT_TIMESTAMP + (? * INTERVAL '1 millisecond')
                FROM candidates
                WHERE event.id = candidates.id
                RETURNING event.*
                """, this::map, batchSize, workerId, lease.toMillis()));
    }

    public boolean complete(OutboxEvent event, OutboxEventHandler handler) {
        Boolean processed = transactions.execute(status -> {
            List<Long> owned = jdbc.queryForList("""
                    SELECT id FROM tb_outbox_event
                    WHERE id = ? AND status = 'PROCESSING' AND locked_by = ?
                    FOR UPDATE
                    """, Long.class, event.id(), event.lockedBy());
            if (owned.isEmpty()) {
                throw new IllegalStateException("Outbox lease ownership was lost before completion");
            }
            int receipt = jdbc.update("""
                    INSERT INTO tb_inbox_receipt (consumer_name, event_id, event_type, processed_at)
                    VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT (consumer_name, event_id) DO NOTHING
                    """, handler.consumerName(), event.eventId(), event.eventType());
            if (receipt == 1) {
                handler.handle(event);
            }
            int updated = jdbc.update("""
                    UPDATE tb_outbox_event
                    SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP,
                        locked_by = NULL, locked_until = NULL, last_error = NULL
                    WHERE id = ? AND status = 'PROCESSING' AND locked_by = ?
                    """, event.id(), event.lockedBy());
            if (updated != 1) {
                throw new IllegalStateException("Claimed outbox event no longer has PROCESSING ownership");
            }
            return receipt == 1;
        });
        return Boolean.TRUE.equals(processed);
    }

    public void retry(OutboxEvent event, RuntimeException failure, int maxAttempts, Duration delay) {
        String message = failure.getClass().getSimpleName();
        if (failure.getMessage() != null && !failure.getMessage().isBlank()) {
            message += ": " + failure.getMessage();
        }
        if (message.length() > MAX_ERROR_LENGTH) {
            message = message.substring(0, MAX_ERROR_LENGTH);
        }
        jdbc.update("""
                UPDATE tb_outbox_event
                SET attempts = attempts + 1,
                    status = CASE WHEN attempts + 1 >= ? THEN 'DEAD' ELSE 'RETRY' END,
                    available_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 millisecond'),
                    locked_by = NULL, locked_until = NULL, last_error = ?
                WHERE id = ? AND status = 'PROCESSING' AND locked_by = ?
                """, maxAttempts, delay.toMillis(), message, event.id(), event.lockedBy());
    }

    private OutboxEvent map(ResultSet rs, int rowNum) throws SQLException {
        return new OutboxEvent(
                rs.getLong("id"),
                rs.getObject("event_id", UUID.class),
                rs.getString("dedup_key"),
                rs.getString("aggregate_type"),
                rs.getString("aggregate_id"),
                rs.getString("event_type"),
                rs.getInt("schema_version"),
                rs.getString("payload"),
                rs.getString("trace_id"),
                rs.getInt("attempts"),
                rs.getString("locked_by"),
                rs.getTimestamp("locked_until") == null ? null : rs.getTimestamp("locked_until").toLocalDateTime());
    }
}
