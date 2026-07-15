package com.innercosmos.config;

import com.innercosmos.dto.LoginRequest;
import com.innercosmos.dto.RegisterRequest;
import com.innercosmos.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@ActiveProfiles("postgres")
@SpringBootTest(properties = {
        "inner-cosmos.demo.seed-enabled=false",
        "llm.provider=mock",
        "llm.mode=dev",
        "spring.task.scheduling.enabled=false"
})
class PostgresApplicationSmokeTest {

    private static final String IMAGE = "pgvector/pgvector:0.8.1-pg16@sha256:"
            + "33198da2828a14c30348d2ccb4750833d5ed9a44c88d840a0e523d7417120337";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("inner_cosmos_application")
            .withUsername("inner_cosmos")
            .withPassword("application-contract-only");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void applicationStartsOnFlywayPostgresAndPersistsIdentityBackedEntities() {
        RegisterRequest register = new RegisterRequest();
        register.username = "postgres_runtime_user";
        register.password = "postgres-contract-password";
        register.nickname = "Postgres Runtime";
        var created = userService.register(register);

        assertNotNull(created.id);
        assertTrue(created.id > 0);

        LoginRequest login = new LoginRequest();
        login.username = register.username;
        login.password = register.password;
        assertEquals(created.id, userService.login(login).id);

        assertEquals(63L, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema='public' AND table_name LIKE 'tb_%'
                """, Long.class));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tb_user WHERE username=?", Long.class, register.username));
    }
}
