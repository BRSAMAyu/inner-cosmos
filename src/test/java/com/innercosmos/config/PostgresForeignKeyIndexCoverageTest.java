package com.innercosmos.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Gemini audit 2.3 (FALSE, see docs/audit/2026-07-23-gemini-master-audit-reconciliation.md
 * section R2): the report claimed {@code tb_memory_embedding.memory_id} and
 * {@code tb_capsule_embedding.capsule_id} have no usable index for a foreign-key lookup and
 * proposed adding a dedicated FK index. That is false -- V10's {@code uk_memory_embedding_version}
 * unique constraint and V18's {@code uk_capsule_embedding_version} unique constraint are each
 * declared with their FK column FIRST, and PostgreSQL always builds a plain B-tree for a UNIQUE
 * constraint, which is usable as a leftmost-prefix index for lookups on that first column alone --
 * an FK-shaped {@code WHERE memory_id = ?} / {@code WHERE capsule_id = ?} query does not need (and
 * must not get) a second, redundant index.
 *
 * This test does not re-derive that conclusion from prose -- it asks the live PostgreSQL catalog
 * (pg_index/pg_attribute) what the real leading column of each unique index is, against the actual
 * Flyway-applied schema, so a future migration change that silently reorders either composite
 * index (and would quietly break this claim) fails this test instead of going unnoticed.
 */
@Testcontainers
class PostgresForeignKeyIndexCoverageTest {

    private static final String IMAGE = "pgvector/pgvector:0.8.1-pg16@sha256:"
            + "33198da2828a14c30348d2ccb4750833d5ed9a44c88d840a0e523d7417120337";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("fk_index_coverage")
            .withUsername("inner_cosmos")
            .withPassword("fk-index-contract-only");

    @Test
    void memoryAndCapsuleEmbeddingUniqueIndexesAlreadyLeadWithTheirForeignKeyColumn() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration/postgresql")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            assertEquals("memory_id", leadingIndexColumn(connection, "uk_memory_embedding_version"),
                    "V10's unique index must still lead with memory_id -- the FK-shaped lookup column");
            assertEquals("capsule_id", leadingIndexColumn(connection, "uk_capsule_embedding_version"),
                    "V18's unique index must still lead with capsule_id -- the FK-shaped lookup column");

            // Belt-and-suspenders: confirm no *other*, redundant single-column index on either FK
            // column exists either -- 2.3's correct contract is "do not add a duplicate index".
            assertEquals(1, singleLeadingColumnIndexCount(connection, "tb_memory_embedding", "memory_id"),
                    "exactly one index should lead with memory_id -- no duplicate FK index");
            assertEquals(1, singleLeadingColumnIndexCount(connection, "tb_capsule_embedding", "capsule_id"),
                    "exactly one index should lead with capsule_id -- no duplicate FK index");
        }
    }

    private static String leadingIndexColumn(Connection connection, String indexName) throws Exception {
        String sql = """
                SELECT a.attname
                FROM pg_index i
                JOIN pg_class ic ON ic.oid = i.indexrelid
                JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = i.indkey[0]
                WHERE ic.relname = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, indexName);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next(), "index " + indexName + " must exist");
                return result.getString(1);
            }
        }
    }

    private static int singleLeadingColumnIndexCount(Connection connection, String table, String column) throws Exception {
        String sql = """
                SELECT COUNT(*)
                FROM pg_index i
                JOIN pg_class ic ON ic.oid = i.indexrelid
                JOIN pg_class tc ON tc.oid = i.indrelid
                JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = i.indkey[0]
                WHERE tc.relname = ? AND a.attname = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }
}
