package com.innercosmos.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SchemaM16InitializerTest {
    @Test
    void longLivedH2AuthorizationRowsAreBackfilledIdempotently() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:m16-legacy;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE tb_echo_capsule(id BIGINT PRIMARY KEY, owner_user_id BIGINT, simulator_only BOOLEAN DEFAULT FALSE)");
        jdbc.execute("CREATE TABLE tb_memory_card(id BIGINT PRIMARY KEY, user_id BIGINT, version_no INT)");
        jdbc.execute("""
                CREATE TABLE tb_authorized_memory_ref(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY, capsule_id BIGINT, memory_card_id BIGINT,
                  authorization_status VARCHAR(32), created_at TIMESTAMP, updated_at TIMESTAMP)
                """);
        jdbc.update("INSERT INTO tb_echo_capsule(id,owner_user_id,simulator_only) VALUES (10,7,FALSE)");
        jdbc.update("INSERT INTO tb_memory_card(id,user_id,version_no) VALUES (20,7,3)");
        jdbc.update("INSERT INTO tb_authorized_memory_ref(capsule_id,memory_card_id,authorization_status,created_at,updated_at) VALUES (10,20,'AUTHORIZED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");

        SchemaM16Initializer initializer = new SchemaM16Initializer(jdbc);
        initializer.run(new DefaultApplicationArguments(new String[0]));
        initializer.run(new DefaultApplicationArguments(new String[0]));

        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM tb_data_use_grant", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM tb_data_use_grant WHERE status='ACTIVE' AND resource_version=3", Integer.class));
        assertNotNull(jdbc.queryForObject("SELECT data_use_grant_id FROM tb_authorized_memory_ref WHERE capsule_id=10", Long.class));
    }
}
