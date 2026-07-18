package com.innercosmos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Schema migration M9: backfills columns schema.sql has always had for fresh installs but
 * which SchemaM8Initializer's manual column list missed. Found by a systematic diff — booted
 * a throwaway fresh H2 database, dumped information_schema.columns for every tb_ table, and
 * diffed it against the same dump from a long-lived local dev database — rather than continuing
 * to fix columns one at a time as each new 500 surfaced. 18 columns across 5 tables were
 * genuinely missing (tb_echo_capsule, tb_memory_card, tb_notification, tb_user_correction,
 * tb_wake_intent); notably tb_user_correction was missing status/confirmed_at/retired_at/
 * impact_summary entirely, meaning the correction confirm/retract lifecycle this session's
 * CorrectionConflictEvaluationTest exercises never worked against this dev database at all.
 */
@Component
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "false", matchIfMissing = true)
@ConditionalOnExpression("'${spring.datasource.driver-class-name:org.h2.Driver}' == 'org.h2.Driver'")
@Order(9)
public class SchemaM9Initializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SchemaM9Initializer.class);

    @Autowired
    private JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        migrate("tb_echo_capsule", "active_genome_version_id BIGINT");
        migrate("tb_memory_card", "archived_at TIMESTAMP NULL");
        migrate("tb_memory_card", "confidence DOUBLE DEFAULT 0.5");
        migrate("tb_memory_card", "consent_scope VARCHAR(32) DEFAULT 'AURORA_PRIVATE'");
        migrate("tb_memory_card", "forgotten_at TIMESTAMP NULL");
        migrate("tb_memory_card", "memory_layer VARCHAR(32) DEFAULT 'EPISODIC'");
        migrate("tb_memory_card", "provenance_refs TEXT");
        migrate("tb_memory_card", "superseded_by_id BIGINT");
        migrate("tb_notification", "idempotency_key VARCHAR(160) NULL");
        migrate("tb_user_correction", "status VARCHAR(32) DEFAULT 'CONFIRMED'");
        migrate("tb_user_correction", "impact_summary TEXT");
        migrate("tb_user_correction", "confirmed_at TIMESTAMP NULL");
        migrate("tb_user_correction", "retired_at TIMESTAMP NULL");
        migrate("tb_wake_intent", "context_session_id BIGINT");
        migrate("tb_wake_intent", "context_message_id BIGINT");
        migrate("tb_wake_intent", "supersedes_intent_id BIGINT");
        migrate("tb_wake_intent", "user_feedback VARCHAR(24)");
        migrate("tb_wake_intent", "feedback_at TIMESTAMP NULL");
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
