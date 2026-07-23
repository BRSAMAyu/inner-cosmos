package com.innercosmos.event.reliable;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcOutboxRepositoryIntegrationTest {
    private static final String IMAGE = "pgvector/pgvector:0.8.1-pg16@sha256:"
            + "33198da2828a14c30348d2ccb4750833d5ed9a44c88d840a0e523d7417120337";
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("outbox_contract")
            .withUsername("outbox")
            .withPassword("test-only-outbox");

    private static HikariDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static JdbcOutboxRepository repository;

    @BeforeAll
    static void start() {
        POSTGRES.start();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        dataSource = new HikariDataSource(config);
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/postgresql")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(dataSource);
        repository = new JdbcOutboxRepository(jdbc, new DataSourceTransactionManager(dataSource));
    }

    @AfterAll
    static void stop() {
        if (dataSource != null) dataSource.close();
        POSTGRES.stop();
    }

    @Test
    void deduplicatesSourceAndConsumerAndAllowsOnlyOneConcurrentClaim() {
        UUID eventId = UUID.randomUUID();
        String dedup = "contract:" + eventId;
        assertThat(repository.append(eventId, dedup, "contract", "1", "contract.v1", 1,
                "{\"value\":1}", "trace-contract")).isTrue();
        assertThat(repository.append(UUID.randomUUID(), dedup, "contract", "1", "contract.v1", 1,
                "{\"value\":1}", "trace-contract")).isFalse();

        List<OutboxEvent> firstClaim = repository.claim("worker-a", 10, Duration.ofSeconds(30));
        assertThat(firstClaim).hasSize(1);
        assertThat(repository.claim("worker-b", 10, Duration.ofSeconds(30))).isEmpty();

        AtomicInteger effects = new AtomicInteger();
        OutboxEventHandler handler = handler("contract.v1", effects, false);
        assertThat(repository.complete(firstClaim.get(0), handler)).isTrue();
        assertThat(effects).hasValue(1);

        jdbc.update("UPDATE tb_outbox_event SET status='PROCESSING', locked_by='worker-a' WHERE id=?",
                firstClaim.get(0).id());
        assertThat(repository.complete(firstClaim.get(0), handler)).isFalse();
        assertThat(effects).hasValue(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_inbox_receipt WHERE event_id=?",
                Integer.class, eventId)).isEqualTo(1);
    }

    @Test
    void expiredLeaseIsRecoveredAndFailureRollsBackReceiptBeforeRetry() throws Exception {
        UUID eventId = UUID.randomUUID();
        repository.append(eventId, "recovery:" + eventId, "contract", "2", "recover.v1", 1,
                "{\"value\":2}", null);
        OutboxEvent claimed = repository.claim("crashed-worker", 1, Duration.ofMillis(150)).get(0);
        assertThat(repository.claim("healthy-worker", 1, Duration.ofSeconds(1))).isEmpty();

        Thread.sleep(200);
        OutboxEvent recovered = repository.claim("healthy-worker", 1, Duration.ofSeconds(1)).get(0);
        assertThat(recovered.eventId()).isEqualTo(claimed.eventId());
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> repository.complete(claimed, handler("recover.v1", new AtomicInteger(), false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ownership");

        AtomicInteger effects = new AtomicInteger();
        OutboxEventHandler failing = handler("recover.v1", effects, true);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> repository.complete(recovered, failing))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_inbox_receipt WHERE event_id=?",
                Integer.class, eventId)).isZero();

        repository.retry(recovered, new IllegalStateException("transient"), 5, Duration.ZERO);
        OutboxEvent retry = repository.claim("healthy-worker", 1, Duration.ofSeconds(1)).get(0);
        assertThat(retry.attempts()).isEqualTo(1);
        assertThat(repository.complete(retry, handler("recover.v1", effects, false))).isTrue();
        assertThat(effects).hasValue(2);
    }

    @Test
    void exhaustedRetriesLandInDeadLetterThenReplayReprocessesExactlyOnce() {
        UUID eventId = UUID.randomUUID();
        repository.append(eventId, "dlq:" + eventId, "contract", "3", "dlq.v1", 1,
                "{\"value\":3}", "trace-dlq");
        AtomicInteger effects = new AtomicInteger();
        OutboxEventHandler failing = handler("dlq.v1", effects, true);

        // Simulate the worker loop: claim -> handler fails (rolls back) -> retry, until MAX_ATTEMPTS (5).
        for (int attempt = 0; attempt < 5; attempt++) {
            OutboxEvent claimed = repository.claim("dlq-worker", 1, Duration.ofSeconds(30)).get(0);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> repository.complete(claimed, failing))
                    .isInstanceOf(IllegalStateException.class);
            repository.retry(claimed, new IllegalStateException("transient"), 5, Duration.ZERO);
        }

        // The event is now dead-lettered and is no longer picked up by normal claims.
        assertThat(statusOf(eventId)).isEqualTo("DEAD");
        assertThat(repository.claim("dlq-worker", 1, Duration.ofSeconds(30))).isEmpty();
        // Failing handler never left a receipt, so no side effect leaked despite 5 attempts.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_inbox_receipt WHERE event_id=?",
                Integer.class, eventId)).isZero();

        // Replay the dead-letter queue: the event returns to PENDING with a clean attempt count.
        assertThat(repository.replayDead(10)).isEqualTo(1);
        assertThat(statusOf(eventId)).isEqualTo("PENDING");

        // A healthy handler now processes it exactly once and it reaches PUBLISHED.
        OutboxEvent replayed = repository.claim("dlq-worker", 1, Duration.ofSeconds(30)).get(0);
        assertThat(replayed.eventId()).isEqualTo(eventId);
        assertThat(replayed.attempts()).isZero();
        assertThat(repository.complete(replayed, handler("dlq.v1", effects, false))).isTrue();
        assertThat(statusOf(eventId)).isEqualTo("PUBLISHED");
        assertThat(effects).hasValue(6); // 5 failed attempts + 1 successful replay
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_inbox_receipt WHERE event_id=?",
                Integer.class, eventId)).isEqualTo(1);
    }

    @Test
    void queuePressureQueriesReflectReadyAgeAndDeadLetterTruthWithoutPayloadAccess() {
        long readyBefore = repository.readyCount();
        long deadBefore = repository.deadCount();
        UUID eventId = UUID.randomUUID();
        repository.append(eventId, "metrics:" + eventId, "contract", "4", "metrics.v1", 1,
                "{\"privateValue\":\"must-never-be-a-metric-tag\"}", "trace-metrics");
        jdbc.update("UPDATE tb_outbox_event SET available_at=CURRENT_TIMESTAMP - INTERVAL '20 seconds' "
                + "WHERE event_id=?", eventId);

        assertThat(repository.readyCount()).isEqualTo(readyBefore + 1);
        assertThat(repository.oldestReadyAgeSeconds()).isGreaterThanOrEqualTo(19.0);

        OutboxEvent claimed = repository.claim("metrics-worker", 1, Duration.ofSeconds(30)).get(0);
        repository.retry(claimed, new IllegalStateException("terminal"), 1, Duration.ZERO);

        assertThat(repository.readyCount()).isEqualTo(readyBefore);
        assertThat(repository.deadCount()).isEqualTo(deadBefore + 1);
    }

    private String statusOf(UUID eventId) {
        return jdbc.queryForObject("SELECT status FROM tb_outbox_event WHERE event_id=?",
                String.class, eventId);
    }

    private OutboxEventHandler handler(String eventType, AtomicInteger effects, boolean fail) {
        return new OutboxEventHandler() {
            @Override public String eventType() { return eventType; }
            @Override public String consumerName() { return "contract-consumer"; }
            @Override public void handle(OutboxEvent event) {
                effects.incrementAndGet();
                if (fail) throw new IllegalStateException("transient handler failure");
            }
        };
    }
}
