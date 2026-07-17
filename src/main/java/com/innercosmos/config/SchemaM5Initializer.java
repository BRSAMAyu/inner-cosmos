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
 * Schema migration M5: data-integrity hardening (VS-006).
 *
 * Adds the UNIQUE (user_id, record_date) constraint to tb_daily_record so that
 * duplicate daily records per user/day cannot be persisted on an existing H2
 * file database (fresh installs already get it from schema.sql).
 *
 * Idempotency: H2 (MODE=MySQL) does not support
 * {@code ALTER TABLE ... ADD CONSTRAINT IF NOT EXISTS}, so the constraint is
 * added only when it is absent — checked against information_schema. Any
 * residual failure (e.g. duplicate rows blocking the constraint, or the
 * constraint already existing) is caught and logged at debug level so startup
 * never fails because of this migration.
 */
@Component
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "false", matchIfMissing = true)
@ConditionalOnExpression("'${spring.datasource.driver-class-name:org.h2.Driver}' == 'org.h2.Driver'")
@Order(5)
public class SchemaM5Initializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SchemaM5Initializer.class);

    private static final String CONSTRAINT_NAME = "uk_daily_record_user_date";

    @Autowired
    private JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        addUniqueConstraintIfAbsent();
    }

    private void addUniqueConstraintIfAbsent() {
        try {
            Integer existing = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.table_constraints
                    WHERE upper(table_name) = 'TB_DAILY_RECORD'
                      AND upper(constraint_type) = 'UNIQUE'
                      AND upper(constraint_name) = ?
                    """, Integer.class, CONSTRAINT_NAME.toUpperCase());
            if (existing != null && existing > 0) {
                log.debug("Unique constraint {} already exists on tb_daily_record — skipped", CONSTRAINT_NAME);
                return;
            }
            String ddl = "ALTER TABLE tb_daily_record ADD CONSTRAINT " + CONSTRAINT_NAME
                    + " UNIQUE (user_id, record_date)";
            try {
                jdbc.execute(ddl);
                log.info("Schema migration applied: {}", ddl);
            } catch (Exception inner) {
                // Either duplicate rows prevent the constraint, or the constraint
                // already exists under a slightly different metadata signature.
                // Both are safe to surface as a warn — startup must not break.
                log.warn("Could not apply unique constraint {} on tb_daily_record (may already exist or duplicate rows present): {}",
                        CONSTRAINT_NAME, inner.getMessage());
            }
        } catch (Exception e) {
            // information_schema probe itself failed — fall back to a guarded ALTER.
            log.debug("information_schema probe failed for tb_daily_record ({}); attempting guarded ALTER", e.getMessage());
            String ddl = "ALTER TABLE tb_daily_record ADD CONSTRAINT " + CONSTRAINT_NAME
                    + " UNIQUE (user_id, record_date)";
            try {
                jdbc.execute(ddl);
                log.info("Schema migration applied (fallback): {}", ddl);
            } catch (Exception inner) {
                log.debug("Fallback unique-constraint migration skipped for tb_daily_record: {}", inner.getMessage());
            }
        }
    }
}
