package com.innercosmos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** H2/local equivalent of PostgreSQL V20 account provenance migration. */
@Component
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "false", matchIfMissing = true)
@ConditionalOnExpression("'${spring.datasource.driver-class-name:org.h2.Driver}' == 'org.h2.Driver'")
@Order(10)
public class SchemaM10Initializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SchemaM10Initializer.class);
    private final JdbcTemplate jdbc;

    public SchemaM10Initializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbc.execute("ALTER TABLE tb_user ADD COLUMN account_kind VARCHAR(16) NOT NULL DEFAULT 'HUMAN'");
            log.info("Schema migration applied: tb_user.account_kind");
        } catch (Exception exists) {
            log.debug("Schema migration skipped for tb_user.account_kind: {}", exists.getMessage());
        }
        jdbc.update("""
                UPDATE tb_user SET account_kind = 'SYNTHETIC'
                WHERE account_kind = 'HUMAN' AND (
                  LOWER(username) LIKE 'csrf%' OR LOWER(username) LIKE 'smoke%'
                  OR LOWER(username) LIKE 'header%' OR LOWER(username) LIKE 'qa-%'
                  OR LOWER(username) LIKE 'test-%' OR LOWER(username) LIKE 'b0observer%'
                  OR LOWER(username) LIKE 'b0-observer%' OR LOWER(username) LIKE 'b0diag%'
                  OR LOWER(username) LIKE 'b1register%' OR LOWER(username) LIKE 'b1routes%'
                  OR LOWER(username) LIKE 'b1loading%' OR LOWER(username) LIKE 'b1pwa%'
                  OR LOWER(username) LIKE 'b1decompose%' OR LOWER(username) LIKE 'b1pwainst%'
                  OR LOWER(username) LIKE 'mobcheck%' OR LOWER(username) LIKE 'ordercheck%'
                  OR LOWER(username) LIKE 'spacecheck%' OR LOWER(username) LIKE 'handoff\\_%' ESCAPE '\\'
                  OR LOWER(username) LIKE 'head\\_%' ESCAPE '\\'
                )
                """);
        jdbc.update("UPDATE tb_user SET account_kind = 'SYSTEM' WHERE username = 'admin'");
        jdbc.update("UPDATE tb_user SET account_kind = 'DEMO' WHERE username = 'demo'");
        jdbc.update("UPDATE tb_user SET account_kind = 'SHOWCASE' WHERE username IN ('river', 'cloud')");
        try {
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_user_discovery ON tb_user(status, account_kind, last_login_at DESC, id DESC)");
        } catch (Exception unsupported) {
            log.debug("Discovery index already exists or is unsupported: {}", unsupported.getMessage());
        }
    }
}
