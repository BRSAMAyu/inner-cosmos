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
 * Schema migrations for M2 proactive engine.
 * H2 does not support ADD COLUMN IF NOT EXISTS, so each statement is attempted
 * and any "column already exists" failure is ignored.
 */
@Component
@Order(1)
public class SchemaM2Initializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SchemaM2Initializer.class);

    @Autowired
    private JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        // ALTER tb_user_profile for proactive settings
        migrate("tb_user_profile", "proactive_intensity VARCHAR(16) DEFAULT 'COMPANION'");
        migrate("tb_user_profile", "sleep_window_start TIME DEFAULT '23:00:00'");
        migrate("tb_user_profile", "sleep_window_end TIME DEFAULT '07:00:00'");
        migrate("tb_user_profile", "boost_until TIMESTAMP NULL");

        // Create new tables
        createTable("""
            CREATE TABLE IF NOT EXISTS tb_proactive_event_log (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              user_id BIGINT NOT NULL,
              event_type VARCHAR(64) NOT NULL,
              trigger_meta TEXT,
              content TEXT NOT NULL,
              sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
              user_responded_at TIMESTAMP NULL,
              accepted BOOLEAN,
              decision_source VARCHAR(16) DEFAULT 'SCHEDULED',
              reason_internal TEXT
            )
            """);

        createTable("""
            CREATE TABLE IF NOT EXISTS tb_private_timer (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              user_id BIGINT NOT NULL,
              fire_at TIMESTAMP NOT NULL,
              kind VARCHAR(16) NOT NULL,
              content TEXT,
              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
              fired_at TIMESTAMP NULL,
              cancelled_at TIMESTAMP NULL
            )
            """);
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

    private void createTable(String ddl) {
        try {
            jdbc.execute(ddl);
            log.info("Table created: {}", ddl.substring(0, Math.min(50, ddl.length())) + "...");
        } catch (Exception e) {
            log.debug("Table creation skipped: {}", e.getMessage());
        }
    }
}