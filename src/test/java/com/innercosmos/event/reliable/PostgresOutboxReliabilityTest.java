package com.innercosmos.event.reliable;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-39 outbox lifecycle on REAL PostgreSQL: the SKIP LOCKED claim, inbox-receipt
 * idempotent redelivery, poison and unknown events exhausting retries into DEAD, and
 * replayDead() reviving them. These are exactly the semantics an H2 twin cannot prove.
 * Docker-gated alongside the other Postgres contract tests.
 */
@Testcontainers(disabledWithoutDocker = true)
@org.junit.jupiter.api.condition.EnabledIf(value = "pgvectorImageResolvesLocally",
        disabledReason = "pgvector digest not resolvable locally and registry unreachable — run where the image was pulled")
class PostgresOutboxReliabilityTest {

    /** Digest-pinned references HEAD the registry when not locally resolvable; a blocked registry must skip, not hang 2 minutes. */
    static boolean pgvectorImageResolvesLocally() {
        try {
            Process process = new ProcessBuilder("docker", "image", "inspect",
                    "pgvector/pgvector@sha256:" + IMAGE.substring(IMAGE.indexOf("sha256:") + 7))
                    .redirectErrorStream(true).start();
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static final String IMAGE = "pgvector/pgvector:0.8.1-pg16@sha256:"
            + "33198da2828a14c30348d2ccb47508d3d5ed9a44c88d840a0e523d7417120337";
    private static final int MAX_ATTEMPTS = 5;

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("inner_cosmos_outbox")
            .withUsername("outbox")
            .withPassword("outbox-reliability-contract");

    private static JdbcOutboxRepository repository;
    private static DataRetractedProjectionHandler retractionHandler;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateAndWire() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration/postgresql")
                .load().migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        repository = new JdbcOutboxRepository(jdbc, new DataSourceTransactionManager(dataSource));
        // CP-15: the handler now also drives the per-asset derivative cleanup; this test pins
        // the outbox lifecycle itself, so it injects a no-op executor (functional interface)
        // instead of the full H2/Spring-backed implementation.
        retractionHandler = new DataRetractedProjectionHandler(new ObjectMapper(), command -> List.of());
    }

    @Test
    void dedupAppendInboxIdempotencyDeadAndReplayAllHoldOnRealPostgres() {
        // 1) Dedup-key append idempotency.
        String key = "pg-idem:" + UUID.randomUUID();
        assertTrue(append(key, DataRetractedOutboxWriter.EVENT_TYPE,
                "{\"receiptId\":1,\"userId\":7,\"subjectType\":\"MEMORY\",\"subjectId\":2,"
                        + "\"derivativeType\":\"MEMORY_EMBEDDING\",\"action\":\"ERASED\"}"));
        assertFalse(append(key, DataRetractedOutboxWriter.EVENT_TYPE, "{}"),
                "same dedup key appends exactly once");

        // 2) Claim + inbox-receipt idempotent redelivery.
        OutboxEvent event = repository.claim("pg-worker", 10, Duration.ofSeconds(30)).stream()
                .filter(candidate -> key.equals(candidate.dedupKey())).findFirst().orElseThrow();
        assertTrue(repository.complete(event, retractionHandler));
        assertFalse(repository.complete(event, retractionHandler),
                "redelivery must not re-run the handler");

        // 3) Poison payload and unknown type exhaust into DEAD.
        String poisonKey = "pg-poison:" + UUID.randomUUID();
        append(poisonKey, DataRetractedOutboxWriter.EVENT_TYPE,
                "{\"receiptId\":null,\"userId\":7}");
        String unknownKey = "pg-unknown:" + UUID.randomUUID();
        append(unknownKey, "no.such.event.v1", "{}");
        for (int round = 0; round < MAX_ATTEMPTS; round++) {
            for (OutboxEvent claimed : repository.claim("pg-poisoner", 10, Duration.ofSeconds(30))) {
                try {
                    retractionHandler.handle(claimed);
                    repository.complete(claimed, retractionHandler);
                } catch (RuntimeException failure) {
                    repository.retry(claimed, failure, MAX_ATTEMPTS, Duration.ZERO);
                }
            }
        }
        assertEquals(1, count(poisonKey, "DEAD"));
        assertEquals(1, count(unknownKey, "DEAD"));

        // 4) replayDead revives for bounded human retry.
        assertTrue(repository.replayDead(10) >= 2);
        assertEquals(0, count(poisonKey, "DEAD"));
        assertEquals(0, count(unknownKey, "DEAD"));
    }

    private boolean append(String dedupKey, String eventType, String payload) {
        return repository.append(UUID.randomUUID(), dedupKey, "test", "test",
                eventType, 1, payload, null);
    }

    private int count(String dedupKey, String status) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_outbox_event WHERE dedup_key = ? AND status = ?",
                Integer.class, dedupKey, status);
        return count == null ? 0 : count;
    }
}
