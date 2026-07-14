package com.innercosmos.poc;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class PostgresContractTest {

    private static final String IMAGE = "pgvector/pgvector:0.8.1-pg16@sha256:"
            + "33198da2828a14c30348d2ccb4750833d5ed9a44c88d840a0e523d7417120337";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("inner_cosmos_poc")
            .withUsername("inner_cosmos")
            .withPassword("contract-only");

    private static SqlSessionFactory sessions;

    @BeforeAll
    static void migrateContractSchema() {
        migrate("contract", null).migrate();
        sessions = sessionFactory("contract");
    }

    @Test
    void flywaySupportsEmptyInstallAndRepeatStartup() throws Exception {
        String schema = "empty_install_" + suffix();
        Flyway flyway = migrate(schema, null);

        assertEquals(2, flyway.migrate().migrationsExecuted);
        assertEquals(0, flyway.migrate().migrationsExecuted);

        try (Connection connection = connection(schema);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT extversion FROM pg_extension WHERE extname='vector'")) {
            assertTrue(result.next());
            assertEquals("0.8.1", result.getString(1));
        }
    }

    @Test
    void flywayUpgradesV1WithoutLosingExistingRows() throws Exception {
        String schema = "upgrade_install_" + suffix();
        Flyway v1 = migrate(schema, MigrationVersion.fromVersion("1"));
        assertEquals(1, v1.migrate().migrationsExecuted);

        UUID sessionId = UUID.randomUUID();
        try (Connection connection = connection(schema);
             var insert = connection.prepareStatement(
                     "INSERT INTO conversation_session(id,user_id,status,context) "
                             + "VALUES (?,42,'ACTIVE','{\"source\":\"v1\"}'::jsonb)")) {
            insert.setObject(1, sessionId);
            assertEquals(1, insert.executeUpdate());
        }

        Flyway latest = migrate(schema, null);
        assertEquals(1, latest.migrate().migrationsExecuted);
        assertEquals(0, latest.migrate().migrationsExecuted);

        try (Connection connection = connection(schema);
             var query = connection.prepareStatement(
                     "SELECT context->>'source' FROM conversation_session WHERE id=?")) {
            query.setObject(1, sessionId);
            try (ResultSet result = query.executeQuery()) {
                assertTrue(result.next());
                assertEquals("v1", result.getString(1));
            }
        }
        try (Connection connection = connection(schema);
             ResultSet result = connection.getMetaData().getColumns(
                     null, schema, "memory_item", "provenance")) {
            assertTrue(result.next(), "V2 provenance column must exist");
        }
        try (Connection connection = connection(schema);
             ResultSet result = connection.getMetaData().getColumns(
                     null, schema, "message", "generation_trace")) {
            assertTrue(result.next(), "V2 generation trace column must exist");
        }
    }

    @Test
    void mybatisPersistsJsonbArraysEnumsAndTimestamps() {
        UUID sessionId = UUID.randomUUID();
        UUID memoryId = UUID.randomUUID();

        try (SqlSession sqlSession = sessions.openSession(true)) {
            PocMapper mapper = sqlSession.getMapper(PocMapper.class);
            assertEquals(1, mapper.insertConversation(new ConversationRow(
                    sessionId, 42L, "ACTIVE", "{\"intent\":\"reflect\"}")));
            assertEquals(1, mapper.insertMemory(new MemoryRow(
                    memoryId, 42L, "EPISODIC", "A calm presentation rehearsal",
                    "{\"confidence\":0.9}", "calm,focus", "AURORA")));

            Map<String, Object> conversation = mapper.findConversation(sessionId);
            assertEquals(42L, ((Number) conversation.get("user_id")).longValue());
            assertTrue(conversation.get("context_json").toString().contains("reflect"));
            assertNotNull(conversation.get("started_at"));

            Map<String, Object> memory = mapper.findMemory(memoryId);
            assertEquals("EPISODIC", memory.get("memory_type"));
            assertTrue(memory.get("attributes_json").toString().contains("0.9"));
            assertEquals("{calm,focus}", memory.get("keywords_text"));
        }
    }

    @Test
    void transactionFailureRollsBackEveryWrite() {
        UUID sessionId = UUID.randomUUID();

        try (SqlSession sqlSession = sessions.openSession(false)) {
            PocMapper mapper = sqlSession.getMapper(PocMapper.class);
            mapper.insertConversation(new ConversationRow(
                    sessionId, 73L, "ACTIVE", "{}"));
            assertThrows(RuntimeException.class, () -> mapper.insertMessage(
                    UUID.randomUUID(), UUID.randomUUID(), 73L, "INVALID", "must fail"));
            sqlSession.rollback();
        }

        try (SqlSession sqlSession = sessions.openSession(true)) {
            assertEquals(0, sqlSession.getMapper(PocMapper.class).countConversation(sessionId));
        }
    }

    @Test
    void databaseRejectsCrossOwnerChildRecords() {
        UUID sessionId = UUID.randomUUID();
        try (SqlSession sqlSession = sessions.openSession(true)) {
            PocMapper mapper = sqlSession.getMapper(PocMapper.class);
            assertEquals(1, mapper.insertConversation(new ConversationRow(
                    sessionId, 51001L, "ACTIVE", "{}")));
            assertThrows(RuntimeException.class, () -> mapper.insertMessage(
                    UUID.randomUUID(), sessionId, 51002L, "USER", "cross-owner write"));
        }

        try (Connection connection = connection("contract");
             var query = connection.prepareStatement(
                     "SELECT COUNT(*) FROM message WHERE session_id=?")) {
            query.setObject(1, sessionId);
            try (ResultSet result = query.executeQuery()) {
                assertTrue(result.next());
                assertEquals(0, result.getInt(1));
            }
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    void mybatisBatchExecutorCommitsAllRows() {
        long userId = 88001L;
        try (SqlSession sqlSession = sessions.openSession(ExecutorType.BATCH, false)) {
            PocMapper mapper = sqlSession.getMapper(PocMapper.class);
            for (int index = 0; index < 100; index++) {
                mapper.insertMemory(new MemoryRow(
                        UUID.randomUUID(), userId, "SEMANTIC", "batch-" + index,
                        "{\"batch\":true}", "batch,postgres", "AURORA"));
            }
            assertFalse(sqlSession.flushStatements().isEmpty());
            sqlSession.commit();
        }

        try (SqlSession sqlSession = sessions.openSession(true)) {
            assertEquals(100, sqlSession.getMapper(PocMapper.class).countMemoryByUser(userId));
        }
    }

    @Test
    void databaseConstraintsRejectInvalidEnumValues() {
        try (SqlSession sqlSession = sessions.openSession(true)) {
            PocMapper mapper = sqlSession.getMapper(PocMapper.class);
            assertThrows(RuntimeException.class, () -> mapper.insertConversation(
                    new ConversationRow(UUID.randomUUID(), 42L, "UNKNOWN", "{}")));
        }
    }

    private static Flyway migrate(String schema, MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private static SqlSessionFactory sessionFactory(String schema) {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                POSTGRES.getDriverClassName(), jdbcUrl(schema),
                POSTGRES.getUsername(), POSTGRES.getPassword());
        Configuration configuration = new Configuration(new Environment(
                "postgres-poc", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(false);
        configuration.getTypeHandlerRegistry().register(UUID.class, new UuidTypeHandler());
        configuration.addMapper(PocMapper.class);
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private static Connection connection(String schema) throws Exception {
        return java.sql.DriverManager.getConnection(
                jdbcUrl(schema), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static String jdbcUrl(String schema) {
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        return POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    record ConversationRow(UUID id, long userId, String status, String contextJson) {}

    record MemoryRow(UUID id, long userId, String memoryType, String content,
                     String attributesJson, String keywordsCsv, String consentScope) {}

    interface PocMapper {
        @Insert("""
                INSERT INTO conversation_session(id,user_id,status,context)
                VALUES (#{id},#{userId},#{status},CAST(#{contextJson} AS jsonb))
                """)
        int insertConversation(ConversationRow row);

        @Insert("""
                INSERT INTO message(id,session_id,user_id,role,content)
                VALUES (#{id},#{sessionId},#{userId},#{role},#{content})
                """)
        int insertMessage(@Param("id") UUID id, @Param("sessionId") UUID sessionId,
                          @Param("userId") long userId, @Param("role") String role,
                          @Param("content") String content);

        @Insert("""
                INSERT INTO memory_item(id,user_id,memory_type,content,attributes,keywords,consent_scope)
                VALUES (#{id},#{userId},#{memoryType},#{content},
                        CAST(#{attributesJson} AS jsonb),string_to_array(#{keywordsCsv},','),#{consentScope})
                """)
        int insertMemory(MemoryRow row);

        @Select("""
                SELECT id,user_id,status,CAST(context AS text) AS context_json,started_at
                FROM conversation_session WHERE id=#{id}
                """)
        Map<String, Object> findConversation(UUID id);

        @Select("""
                SELECT id,user_id,memory_type,CAST(attributes AS text) AS attributes_json,
                       CAST(keywords AS text) AS keywords_text
                FROM memory_item WHERE id=#{id}
                """)
        Map<String, Object> findMemory(UUID id);

        @Select("SELECT COUNT(*) FROM conversation_session WHERE id=#{id}")
        int countConversation(UUID id);

        @Select("SELECT COUNT(*) FROM memory_item WHERE user_id=#{userId}")
        int countMemoryByUser(long userId);
    }

    static final class UuidTypeHandler extends BaseTypeHandler<UUID> {
        @Override
        public void setNonNullParameter(PreparedStatement statement, int index,
                                        UUID parameter, JdbcType jdbcType) throws SQLException {
            statement.setObject(index, parameter);
        }

        @Override
        public UUID getNullableResult(ResultSet result, String columnName) throws SQLException {
            return result.getObject(columnName, UUID.class);
        }

        @Override
        public UUID getNullableResult(ResultSet result, int columnIndex) throws SQLException {
            return result.getObject(columnIndex, UUID.class);
        }

        @Override
        public UUID getNullableResult(CallableStatement statement, int columnIndex) throws SQLException {
            return statement.getObject(columnIndex, UUID.class);
        }
    }
}
