package com.innercosmos.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class PostgresFlywayBaselineTest {

    private static final String IMAGE = "pgvector/pgvector:0.8.1-pg16@sha256:"
            + "33198da2828a14c30348d2ccb4750833d5ed9a44c88d840a0e523d7417120337";
    private static final Pattern SOURCE_TABLE = Pattern.compile(
            "(?m)^CREATE TABLE IF NOT EXISTS\\s+(tb_[a-z0-9_]+)\\s*\\(");
    private static final Pattern SOURCE_INDEX = Pattern.compile(
            "(?m)^\\s*(?:UNIQUE\\s+(?:KEY|INDEX)|INDEX)\\s+([a-z0-9_]+)\\s*\\(");
    private static final Pattern SOURCE_FOREIGN_KEY = Pattern.compile(
            "(?m)^\\s*CONSTRAINT\\s+(fk_[a-z0-9_]+)\\s+FOREIGN KEY");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("inner_cosmos_schema")
            .withUsername("inner_cosmos")
            .withPassword("schema-contract-only");

    @Test
    void flywayCreatesExactApplicationTableIndexAndForeignKeyBaseline() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration/postgresql")
                .load();

        assertEquals(6, flyway.migrate().migrationsExecuted);
        assertEquals(0, flyway.migrate().migrationsExecuted);

        String source = readClasspath("schema.sql");
        Set<String> expectedTables = matches(source, SOURCE_TABLE);
        Set<String> expectedIndexes = matches(source, SOURCE_INDEX);
        Set<String> expectedForeignKeys = matches(source, SOURCE_FOREIGN_KEY);

        try (Connection connection = connection()) {
            Set<String> actualTables = values(connection, """
                    SELECT table_name FROM information_schema.tables
                    WHERE table_schema='public' AND table_name LIKE 'tb_%'
                    """);
            Set<String> actualIndexes = values(connection, """
                    SELECT indexname FROM pg_indexes
                    WHERE schemaname='public' AND indexname NOT LIKE '%_pkey'
                    """);
            Set<String> actualForeignKeys = values(connection, """
                    SELECT constraint_name FROM information_schema.table_constraints
                    WHERE constraint_schema='public' AND constraint_type='FOREIGN KEY'
                    """);

            assertEquals(67, expectedTables.size(), "source schema table inventory changed");
            assertEquals(expectedTables, actualTables, "PostgreSQL baseline table drift");
            assertTrue(actualIndexes.containsAll(expectedIndexes),
                    () -> "missing PostgreSQL indexes: " + difference(expectedIndexes, actualIndexes));
            assertEquals(expectedForeignKeys, actualForeignKeys, "PostgreSQL foreign-key drift");
            assertEquals(65, scalar(connection, """
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema='public' AND is_identity='YES'
                    """));
            assertEquals("0.8.1", textScalar(connection,
                    "SELECT extversion FROM pg_extension WHERE extname='vector'"));
        }
    }

    @Test
    void v3ToV4AcceptsLegitimateDuplicateLegacyNotifications() throws Exception {
        String database = "legacy_notification_" + System.nanoTime();
        try (Connection admin = connection(); Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + database);
        }
        String jdbcUrl = POSTGRES.getJdbcUrl().replace("/inner_cosmos_schema", "/" + database);
        Flyway v3 = Flyway.configure()
            .dataSource(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration/postgresql")
            .target("3")
            .load();
        assertEquals(3, v3.migrate().migrationsExecuted);
        try (Connection legacy = DriverManager.getConnection(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = legacy.createStatement()) {
            statement.executeUpdate("""
                INSERT INTO tb_notification(user_id,type,title,body,ref_id,ref_type,is_read)
                VALUES (91,'SYNC_FAILED','retry 1','first',7,'CAPSULE_SYNC',false),
                       (91,'SYNC_FAILED','retry 2','second',7,'CAPSULE_SYNC',false)
                """);
        }
        Flyway v4 = Flyway.configure()
            .dataSource(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration/postgresql")
            .target("4")
            .load();
        assertEquals(1, v4.migrate().migrationsExecuted);
        try (Connection migrated = DriverManager.getConnection(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword())) {
            assertEquals(2, scalar(migrated,
                "SELECT COUNT(*) FROM tb_notification WHERE user_id=91 AND ref_type='CAPSULE_SYNC' AND ref_id=7"));
        }
    }

    @Test
    void ownerAndUniquenessConstraintsRejectInvalidRows() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration/postgresql")
                .load()
                .migrate();

        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO tb_user(username,password_hash,role,status)
                    VALUES ('postgres-owner','not-a-real-hash','USER','ACTIVE')
                    """);
            assertFalse(statement.execute("""
                    INSERT INTO tb_user_profile(user_id,aurora_name)
                    SELECT id,'Aurora' FROM tb_user WHERE username='postgres-owner'
                    """));
            boolean duplicateRejected = false;
            try {
                statement.executeUpdate("""
                        INSERT INTO tb_user_profile(user_id,aurora_name)
                        SELECT id,'Other' FROM tb_user WHERE username='postgres-owner'
                        """);
            } catch (Exception expected) {
                duplicateRejected = true;
            }
            assertTrue(duplicateRejected, "unique owner profile must be enforced by PostgreSQL");
        }
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static String readClasspath(String name) throws Exception {
        try (var input = PostgresFlywayBaselineTest.class.getClassLoader().getResourceAsStream(name)) {
            if (input == null) {
                throw new IllegalStateException("Missing classpath resource: " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Set<String> matches(String input, Pattern pattern) {
        Set<String> values = new HashSet<>();
        var matcher = pattern.matcher(input);
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    private static Set<String> values(Connection connection, String sql) throws Exception {
        Set<String> values = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                values.add(result.getString(1));
            }
        }
        return values;
    }

    private static long scalar(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private static String textScalar(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private static Set<String> difference(Set<String> expected, Set<String> actual) {
        Set<String> missing = new HashSet<>(expected);
        missing.removeAll(actual);
        return missing;
    }
}
