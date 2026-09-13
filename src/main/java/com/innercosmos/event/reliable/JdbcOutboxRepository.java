package com.innercosmos.event.reliable;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "inner-cosmos.events.outbox.enabled", havingValue = "true")
public class JdbcOutboxRepository {
    private static final int MAX_ERROR_LENGTH = 500;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public JdbcOutboxRepository(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        boolean isPostgres;
        try {
            isPostgres = jdbc.getDataSource() != null && jdbc.getDataSource().getConnection()
                    .getMetaData().getURL().contains(":postgresql:");
        } catch (Exception ignored) {
            isPostgres = false;
        }
        this.postgres = isPostgres;
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** H2 (tests) has no jsonb; PostgreSQL keeps the explicit cast for its jsonb column. */
    private final boolean postgres;

    public boolean append(UUID eventId, String dedupKey, String aggregateType, String aggregateId,
                          String eventType, int schemaVersion, String payload, String traceId) {
        String sql = postgres
                ? """
                INSERT INTO tb_outbox_event
                    (event_id, dedup_key, aggregate_type, aggregate_id, event_type,
                     schema_version, payload, trace_id, status, available_at)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, 'PENDING', CURRENT_TIMESTAMP)
                ON CONFLICT (dedup_key) DO NOTHING
                """
                // H2 twin runs in MySQL compatibility mode: INSERT IGNORE dedups on the
                // unique dedup_key the same way ON CONFLICT DO NOTHING does on PostgreSQL.
                : """
                INSERT IGNORE INTO tb_outbox_event
                    (event_id, dedup_key, aggregate_type, aggregate_id, event_type,
                     schema_version, payload, trace_id, status, available_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP)
                """;
        int inserted = jdbc.update(sql, eventId, dedupKey, aggregateType, aggregateId,
                eventType, schemaVersion, payload, traceId);
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
        // Dialect twin of append(): PostgreSQL keeps its native interval arithmetic; H2 (dev/test)
        // cannot parse `INTERVAL '1 millisecond'` in MySQL mode and uses DATEADD instead. Parameter
        // order is identical in both branches. The claim SQL stays PostgreSQL-only (SKIP LOCKED).
        String sql = postgres
                ? """
                UPDATE tb_outbox_event
                SET attempts = attempts + 1,
                    status = CASE WHEN attempts + 1 >= ? THEN 'DEAD' ELSE 'RETRY' END,
                    available_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 millisecond'),
                    locked_by = NULL, locked_until = NULL, last_error = ?
                WHERE id = ? AND status = 'PROCESSING' AND locked_by = ?
                """
                : """
                UPDATE tb_outbox_event
                SET attempts = attempts + 1,
                    status = CASE WHEN attempts + 1 >= ? THEN 'DEAD' ELSE 'RETRY' END,
                    available_at = DATEADD('MILLISECOND', ?, CURRENT_TIMESTAMP),
                    locked_by = NULL, locked_until = NULL, last_error = ?
                WHERE id = ? AND status = 'PROCESSING' AND locked_by = ?
                """;
        jdbc.update(sql, maxAttempts, delay.toMillis(), message, event.id(), event.lockedBy());
    }

    /**
     * Dead-letter replay: reset up to {@code limit} events that exhausted their retries (status DEAD)
     * back to PENDING so a worker re-processes them from a clean slate. Idempotent consumption still
     * protects against duplicate side effects (tb_inbox_receipt), so replay is safe to re-run. Returns
     * the number of events requeued.
     */
    public int replayDead(int limit) {
        return jdbc.update("""
                UPDATE tb_outbox_event
                SET status = 'PENDING', attempts = 0, available_at = CURRENT_TIMESTAMP,
                    locked_by = NULL, locked_until = NULL, last_error = NULL
                WHERE id IN (
                    SELECT id FROM tb_outbox_event WHERE status = 'DEAD' ORDER BY id LIMIT ?
                )
                """, limit);
    }

    /**
     * Privacy-safe queue pressure used by Prometheus/KEDA. These queries expose counts and age
     * only; event payloads, aggregate identifiers, users and trace identifiers never become tags.
     */
    public long readyCount() {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM tb_outbox_event
                WHERE (status IN ('PENDING', 'RETRY') AND available_at <= CURRENT_TIMESTAMP)
                   OR (status = 'PROCESSING' AND locked_until < CURRENT_TIMESTAMP)
                """, Long.class);
        return count == null ? 0L : count;
    }

    public double oldestReadyAgeSeconds() {
        Double age = jdbc.queryForObject("""
                SELECT COALESCE(
                    EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - MIN(available_at))),
                    0
                )
                FROM tb_outbox_event
                WHERE (status IN ('PENDING', 'RETRY') AND available_at <= CURRENT_TIMESTAMP)
                   OR (status = 'PROCESSING' AND locked_until < CURRENT_TIMESTAMP)
                """, Double.class);
        return age == null ? 0.0 : Math.max(0.0, age);
    }

    public long deadCount() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_outbox_event WHERE status = 'DEAD'", Long.class);
        return count == null ? 0L : count;
    }

    /**
     * CP-40 admin DLQ dashboard: DEAD rows that really exist, newest (highest id) first, page by
     * limit/offset. Read-only — an empty database honestly yields an empty page. Payloads are
     * truncated to a recognisable preview; counts and timestamps come straight from the row.
     */
    public List<OutboxDeadLetter> findDead(int limit, int offset) {
        return jdbc.query("""
                SELECT id, event_id, event_type, aggregate_type, aggregate_id, payload,
                       attempts, last_error, created_at, available_at
                FROM tb_outbox_event
                WHERE status = 'DEAD'
                ORDER BY id DESC
                LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new OutboxDeadLetter(
                rs.getLong("id"),
                rs.getObject("event_id", UUID.class),
                rs.getString("event_type"),
                rs.getString("aggregate_type"),
                rs.getString("aggregate_id"),
                summarizePayload(rs.getString("payload")),
                rs.getInt("attempts"),
                rs.getString("last_error"),
                toLocalDateTime(rs, "created_at"),
                toLocalDateTime(rs, "available_at")), limit, Math.max(offset, 0));
    }

    /**
     * CP-40 per-event replay: requeue exactly the DEAD row identified by {@code eventId} with the
     * same reset semantics as {@link #replayDead(int)} — status PENDING, attempts zeroed, available
     * immediately, lease and last error cleared. The update is guarded by {@code status = 'DEAD'},
     * so replay is single-shot per dead episode: once a row leaves DEAD (replayed, or revived by any
     * other transition) a repeat call updates nothing and returns false rather than silently
     * resetting live state. Duplicate side effects after a replay stay impossible via the
     * {@code tb_inbox_receipt} consumer dedup, exactly as for {@link #replayDead(int)}.
     */
    public boolean replayDead(UUID eventId) {
        return jdbc.update("""
                UPDATE tb_outbox_event
                SET status = 'PENDING', attempts = 0, available_at = CURRENT_TIMESTAMP,
                    locked_by = NULL, locked_until = NULL, last_error = NULL
                WHERE event_id = ? AND status = 'DEAD'
                """, eventId) == 1;
    }

    /** CP-40: current lifecycle status of one outbox event, or null when no such event exists. */
    public String statusOf(UUID eventId) {
        List<String> statuses = jdbc.queryForList(
                "SELECT status FROM tb_outbox_event WHERE event_id = ?", String.class, eventId);
        return statuses.isEmpty() ? null : statuses.get(0);
    }

    private static final int PAYLOAD_SUMMARY_LENGTH = 200;

    private static String summarizePayload(String payload) {
        if (payload == null) {
            return null;
        }
        return payload.length() <= PAYLOAD_SUMMARY_LENGTH
                ? payload
                : payload.substring(0, PAYLOAD_SUMMARY_LENGTH) + "…(truncated)";
    }

    private static LocalDateTime toLocalDateTime(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column) == null ? null : rs.getTimestamp(column).toLocalDateTime();
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
