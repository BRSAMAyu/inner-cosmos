package com.innercosmos.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchemaM10InitializerTest {

    @Test
    void legacyAutomationAccountsAreBackfilledIdempotentlyWithoutNicknameGuessing() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:m10-account-kind;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("""
                CREATE TABLE tb_user(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  username VARCHAR(64) NOT NULL,
                  nickname VARCHAR(64),
                  status VARCHAR(16) NOT NULL,
                  last_login_at TIMESTAMP
                )
                """);
        jdbc.update("INSERT INTO tb_user(username,nickname,status) VALUES ('handoff_21128','Smoke','ACTIVE')");
        jdbc.update("INSERT INTO tb_user(username,nickname,status) VALUES ('head_1784075627305','Header','ACTIVE')");
        jdbc.update("INSERT INTO tb_user(username,nickname,status) VALUES ('real_person','Header enthusiast','ACTIVE')");

        SchemaM10Initializer initializer = new SchemaM10Initializer(jdbc);
        initializer.run(new DefaultApplicationArguments(new String[0]));
        initializer.run(new DefaultApplicationArguments(new String[0]));

        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_user WHERE account_kind='SYNTHETIC'", Integer.class));
        assertEquals("HUMAN", jdbc.queryForObject(
                "SELECT account_kind FROM tb_user WHERE username='real_person'", String.class));
    }
}
