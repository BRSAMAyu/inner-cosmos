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
 * IC-CAP-002 schema migration (H2 + MySQL compatible, guarded):
 *  - tb_echo_capsule       + last_activity_at
 *  - tb_capsule_sync_queue + attempt_count, last_error, failed_at, next_retry_at
 *  - tb_notification       (new table)
 *
 * Each ALTER is guarded by an information_schema column probe and wrapped in try/catch,
 * mirroring {@link SchemaCapsuleQuotaInitializer}.
 */
@Component
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "false", matchIfMissing = true)
@ConditionalOnExpression("'${spring.datasource.driver-class-name:org.h2.Driver}' == 'org.h2.Driver'")
@Order(8)
public class SchemaCapsuleEnergyInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SchemaCapsuleEnergyInitializer.class);

    @Autowired
    private JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        addColumnIfAbsent("TB_ECHO_CAPSULE", "LAST_ACTIVITY_AT",
                "ALTER TABLE tb_echo_capsule ADD COLUMN last_activity_at TIMESTAMP NULL");

        addColumnIfAbsent("TB_CAPSULE_SYNC_QUEUE", "ATTEMPT_COUNT",
                "ALTER TABLE tb_capsule_sync_queue ADD COLUMN attempt_count INT DEFAULT 0");
        addColumnIfAbsent("TB_CAPSULE_SYNC_QUEUE", "LAST_ERROR",
                "ALTER TABLE tb_capsule_sync_queue ADD COLUMN last_error TEXT NULL");
        addColumnIfAbsent("TB_CAPSULE_SYNC_QUEUE", "FAILED_AT",
                "ALTER TABLE tb_capsule_sync_queue ADD COLUMN failed_at TIMESTAMP NULL");
        addColumnIfAbsent("TB_CAPSULE_SYNC_QUEUE", "NEXT_RETRY_AT",
                "ALTER TABLE tb_capsule_sync_queue ADD COLUMN next_retry_at TIMESTAMP NULL");

        createNotificationTableIfAbsent();
    }

    private void addColumnIfAbsent(String table, String column, String alterSql) {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE upper(table_name) = ? AND upper(column_name) = ? AND table_schema = SCHEMA()",
                    Integer.class, table, column);
            if (count != null && count > 0) {
                log.debug("{}.{} already exists — skipped", table, column);
                return;
            }
        } catch (Exception e) {
            log.debug("information_schema probe for {}.{} failed ({}); attempting ALTER",
                    table, column, e.getMessage());
        }
        try {
            jdbc.execute(alterSql);
            log.info("Schema migration applied: added {}.{}", table, column);
        } catch (Exception e) {
            log.warn("Could not add {}.{} (may already exist): {}", table, column, e.getMessage());
        }
    }

    private void createNotificationTableIfAbsent() {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE upper(table_name) = 'TB_NOTIFICATION' AND table_schema = SCHEMA()",
                    Integer.class);
            if (count != null && count > 0) {
                log.debug("tb_notification already exists — skipped");
                return;
            }
        } catch (Exception e) {
            log.debug("information_schema probe for tb_notification failed ({}); proceeding with CREATE", e.getMessage());
        }
        try {
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS tb_notification (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      user_id BIGINT NOT NULL,
                      type VARCHAR(64),
                      title VARCHAR(255),
                      body TEXT,
                      ref_id BIGINT NULL,
                      ref_type VARCHAR(64) NULL,
                      is_read BOOLEAN DEFAULT FALSE,
                      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                      INDEX idx_notification_user (user_id)
                    )
                    """);
            log.info("Schema migration applied: created tb_notification");
        } catch (Exception e) {
            log.warn("Could not create tb_notification (may already exist): {}", e.getMessage());
        }
    }
}
