package com.innercosmos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Lightweight schema migrations for milestones M0 / M6.
 *
 * H2 does not support {@code ALTER TABLE ... ADD COLUMN IF NOT EXISTS}, so each
 * statement is attempted and any "column already exists" failure is ignored.
 * Runs on every startup — fast (two probes) and safe for the H2 dev database.
 */
@Component
@Order(0)
public class SchemaM0M6Initializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SchemaM0M6Initializer.class);

    @Autowired
    private JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        migrate("tb_dialog_session", "preferred_model VARCHAR(32)");
        migrate("tb_user_profile",   "preferred_model VARCHAR(32)");
    }

    private void migrate(String table, String columnDef) {
        String ddl = "ALTER TABLE " + table + " ADD COLUMN " + columnDef;
        try {
            jdbc.execute(ddl);
            log.info("Schema migration applied: {}", ddl);
        } catch (Exception e) {
            // Likely "Column ... already exists" — safe to ignore.
            log.debug("Schema migration skipped for {} ({}): {}", ddl, table, e.getMessage());
        }
    }
}
