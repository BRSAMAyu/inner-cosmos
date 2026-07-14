package com.innercosmos.poc;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class PostgresVectorBenchmarkIT {

    private static final String IMAGE = "pgvector/pgvector:0.8.1-pg16@sha256:"
            + "33198da2828a14c30348d2ccb4750833d5ed9a44c88d840a0e523d7417120337";
    private static final String QUERY = """
            SELECT mi.id,mi.user_id,mi.consent_scope,mi.retention_until,mi.status,
                   me.embedding OPERATOR(public.<=>) CAST(? AS public.vector) AS distance
            FROM memory_item mi
            JOIN memory_embedding me ON me.memory_id=mi.id AND me.user_id=mi.user_id
            WHERE mi.user_id=?
              AND mi.consent_scope IN ('AURORA','CAPSULE')
              AND mi.status='ACTIVE'
              AND (mi.retention_until IS NULL OR mi.retention_until > CURRENT_TIMESTAMP)
              AND me.embedding_type='semantic'
              AND me.model_id='fixture-8d'
              AND me.model_version='v1'
            ORDER BY me.embedding OPERATOR(public.<=>) CAST(? AS public.vector)
            LIMIT 20
            """;
    private static final String VECTOR = "[0.11,0.22,0.33,0.44,0.55,0.66,0.77,0.88]";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("inner_cosmos_benchmark")
            .withUsername("inner_cosmos")
            .withPassword("benchmark-only");

    @Test
    void filteredVectorRetrievalMeetsTenAndHundredThousandRowGates() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("benchmark")
                .defaultSchema("benchmark")
                .createSchemas(true)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = connection()) {
            insertSyntheticRange(connection, 1, 10_000);
            BenchmarkResult tenThousand = benchmark(connection, 10_000, 100.0);

            insertSyntheticRange(connection, 10_001, 100_000);
            BenchmarkResult hundredThousand = benchmark(connection, 100_000, 150.0);

            assertTrue(hundredThousand.plan.contains("user_id"),
                    "query plan must retain the owner predicate inside PostgreSQL");
            assertTrue(hundredThousand.plan.contains("consent_scope"),
                    "query plan must retain consent filtering inside PostgreSQL");
            assertTrue(hundredThousand.plan.contains("retention_until"),
                    "query plan must retain retention filtering inside PostgreSQL");
            assertTrue(hundredThousand.globalRows > hundredThousand.policyRows,
                    "policy-bound candidates must be narrower than the global corpus");

            System.out.printf(Locale.ROOT,
                    "POC_BENCHMARK {\"rows\":10000,\"p50_ms\":%.3f,\"p95_ms\":%.3f,\"p99_ms\":%.3f," +
                            "\"policy_rows\":%d,\"global_rows\":%d}%n",
                    tenThousand.p50Ms, tenThousand.p95Ms, tenThousand.p99Ms,
                    tenThousand.policyRows, tenThousand.globalRows);
            System.out.printf(Locale.ROOT,
                    "POC_BENCHMARK {\"rows\":100000,\"p50_ms\":%.3f,\"p95_ms\":%.3f,\"p99_ms\":%.3f," +
                            "\"policy_rows\":%d,\"global_rows\":%d}%n",
                    hundredThousand.p50Ms, hundredThousand.p95Ms, hundredThousand.p99Ms,
                    hundredThousand.policyRows, hundredThousand.globalRows);
            System.out.println("POC_QUERY_PLAN " + hundredThousand.plan);
        }
    }

    private static void insertSyntheticRange(Connection connection, int start, int end) throws Exception {
        String sql = """
                WITH inserted AS (
                  INSERT INTO memory_item(
                    id,user_id,memory_type,content,attributes,keywords,consent_scope,
                    retention_until,status,version,provenance)
                  SELECT gen_random_uuid(),(g %% 1000)+1,'EPISODIC','synthetic-' || g,
                         jsonb_build_object('fixture',true,'ordinal',g),
                         ARRAY['topic-' || (g %% 37),'phase-' || (g %% 11)],
                         CASE WHEN g %% 3=0 THEN 'CAPSULE' WHEN g %% 3=1 THEN 'AURORA' ELSE 'PRIVATE' END,
                         CASE WHEN g %% 11=0 THEN CURRENT_TIMESTAMP - INTERVAL '1 day'
                              ELSE CURRENT_TIMESTAMP + INTERVAL '30 days' END,
                         CASE WHEN g %% 13=0 THEN 'ARCHIVED' ELSE 'ACTIVE' END,
                         1,jsonb_build_object('source','synthetic-poc')
                  FROM generate_series(%d,%d) AS g
                  RETURNING id,user_id,version,content
                )
                INSERT INTO memory_embedding(
                  id,memory_id,user_id,embedding_type,model_id,model_version,source_version,embedding)
                SELECT gen_random_uuid(),id,user_id,'semantic','fixture-8d','v1',version,
                       format('[%%s,%%s,%%s,%%s,%%s,%%s,%%s,%%s]',
                         (ordinal %% 97)::double precision/97,
                         (ordinal %% 89)::double precision/89,
                         (ordinal %% 83)::double precision/83,
                         (ordinal %% 79)::double precision/79,
                         (ordinal %% 73)::double precision/73,
                         (ordinal %% 71)::double precision/71,
                         (ordinal %% 67)::double precision/67,
                         (ordinal %% 61)::double precision/61)::public.vector
                FROM (
                  SELECT *,substring(content from 11)::bigint AS ordinal FROM inserted
                ) source
                """.formatted(start, end);
        try (Statement statement = connection.createStatement()) {
            assertEquals(end - start + 1, statement.executeUpdate(sql));
            statement.execute("ANALYZE memory_item");
            statement.execute("ANALYZE memory_embedding");
        }
    }

    private static BenchmarkResult benchmark(Connection connection, int expectedRows,
                                             double p95GateMs) throws Exception {
        assertEquals(expectedRows, count(connection, "SELECT COUNT(*) FROM memory_item"));
        long policyRows = count(connection, """
                SELECT COUNT(*) FROM memory_item
                WHERE user_id=42 AND consent_scope IN ('AURORA','CAPSULE') AND status='ACTIVE'
                  AND (retention_until IS NULL OR retention_until > CURRENT_TIMESTAMP)
                """);

        for (int index = 0; index < 25; index++) {
            executeQuery(connection, 42L);
        }
        List<Double> timings = new ArrayList<>();
        for (int index = 0; index < 200; index++) {
            long started = System.nanoTime();
            executeQuery(connection, 42L);
            timings.add((System.nanoTime() - started) / 1_000_000.0);
        }
        Collections.sort(timings);
        double p50 = percentile(timings, 0.50);
        double p95 = percentile(timings, 0.95);
        double p99 = percentile(timings, 0.99);
        assertTrue(p95 <= p95GateMs,
                () -> "p95 " + p95 + " ms exceeded gate " + p95GateMs + " ms");

        String plan;
        try (PreparedStatement statement = connection.prepareStatement(
                "EXPLAIN (ANALYZE,BUFFERS,FORMAT JSON) " + QUERY)) {
            bind(statement, 42L);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                plan = result.getString(1).replaceAll("\\s+", " ");
            }
        }
        return new BenchmarkResult(p50, p95, p99, policyRows,
                count(connection, "SELECT COUNT(*) FROM memory_item"), plan);
    }

    private static void executeQuery(Connection connection, long userId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(QUERY)) {
            bind(statement, userId);
            try (ResultSet result = statement.executeQuery()) {
                int rows = 0;
                while (result.next()) {
                    rows++;
                    assertEquals(userId, result.getLong("user_id"));
                    assertTrue(result.getString("consent_scope").equals("AURORA")
                            || result.getString("consent_scope").equals("CAPSULE"));
                    assertEquals("ACTIVE", result.getString("status"));
                    OffsetDateTime retention = result.getObject("retention_until", OffsetDateTime.class);
                    assertTrue(retention == null || retention.isAfter(OffsetDateTime.now().minusSeconds(1)));
                }
                assertTrue(rows > 0 && rows <= 20);
            }
        }
    }

    private static void bind(PreparedStatement statement, long userId) throws Exception {
        statement.setString(1, VECTOR);
        statement.setLong(2, userId);
        statement.setString(3, VECTOR);
    }

    private static long count(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private static double percentile(List<Double> sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }

    private static Connection connection() throws Exception {
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        return DriverManager.getConnection(POSTGRES.getJdbcUrl() + separator + "currentSchema=benchmark",
                POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private record BenchmarkResult(double p50Ms, double p95Ms, double p99Ms,
                                   long policyRows, long globalRows, String plan) {}
}
