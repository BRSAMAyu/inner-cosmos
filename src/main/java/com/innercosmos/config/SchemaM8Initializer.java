package com.innercosmos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Schema migration M8: backfills columns that were added to schema.sql's CREATE TABLE
 * definitions (so fresh installs always had them) without a matching idempotent ALTER
 * migration for pre-existing H2 databases — discovered live against a long-lived local dev
 * database, where their absence caused real 500s (BadSqlGrammarException: Column not found)
 * on wake-intents, self/evolution, starfield, notifications, memory cards, capsules, and
 * matching. PostgreSQL/Flyway already has these columns correctly via the versioned migration
 * chain (verified by PostgresFlywayBaselineTest); this is specifically an H2 dev/demo-mode gap.
 */
@Component
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "false", matchIfMissing = true)
@Order(8)
public class SchemaM8Initializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SchemaM8Initializer.class);

    @Autowired
    private JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        migrate("tb_user_profile", "timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Singapore'");
        migrate("tb_memory_card", "version_no INT DEFAULT 1");
        migrate("tb_aurora_self_statement", "trigger_type VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN'");
        migrate("tb_aurora_self_reflection", "trigger_type VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN'");
        migrate("tb_capsule_genome_version", "version_no INT NOT NULL DEFAULT 1");
        migrate("tb_aurora_self_version", "version_no INT NOT NULL DEFAULT 1");
        migrate("tb_wake_intent", "timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Singapore'");
    }

    private void migrate(String table, String columnDef) {
        String ddl = "ALTER TABLE " + table + " ADD COLUMN " + columnDef;
        try {
            jdbc.execute(ddl);
            log.info("Schema migration applied: {}", ddl);
        } catch (Exception e) {
            log.debug("Schema migration skipped for {} ({}): {}", ddl, table, e.getMessage());
        }
    }
}
