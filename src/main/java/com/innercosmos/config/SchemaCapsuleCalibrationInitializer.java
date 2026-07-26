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

/**
 * H2/local equivalent of PostgreSQL V25 for long-lived development databases. Fresh databases
 * already receive these columns from schema.sql; each ALTER is intentionally independently
 * idempotent for existing files.
 */
@Component
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "false", matchIfMissing = true)
@ConditionalOnExpression("'${spring.datasource.driver-class-name:org.h2.Driver}' == 'org.h2.Driver'")
@Order(25)
public class SchemaCapsuleCalibrationInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SchemaCapsuleCalibrationInitializer.class);
    private final JdbcTemplate jdbc;

    public SchemaCapsuleCalibrationInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        addColumn("ALTER TABLE tb_capsule_sandbox_feedback ADD COLUMN calibration_signals_json TEXT",
                "calibration_signals_json");
        addColumn("ALTER TABLE tb_capsule_sandbox_feedback ADD COLUMN applied_genome_version_id BIGINT",
                "applied_genome_version_id");
        try {
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_capsule_sandbox_feedback_status "
                    + "ON tb_capsule_sandbox_feedback(capsule_id, status, created_at)");
        } catch (Exception existsOrUnsupported) {
            log.debug("Capsule calibration status index already exists or is unsupported: {}",
                    existsOrUnsupported.getMessage());
        }
    }

    private void addColumn(String sql, String name) {
        try {
            jdbc.execute(sql);
            log.info("Schema migration applied: tb_capsule_sandbox_feedback.{}", name);
        } catch (Exception exists) {
            log.debug("Schema migration skipped for tb_capsule_sandbox_feedback.{}: {}", name, exists.getMessage());
        }
    }
}
