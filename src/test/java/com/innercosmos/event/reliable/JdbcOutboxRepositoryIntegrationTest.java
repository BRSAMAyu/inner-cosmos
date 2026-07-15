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
