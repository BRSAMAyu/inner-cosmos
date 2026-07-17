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

@Component
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "false", matchIfMissing = true)
@ConditionalOnExpression("'${spring.datasource.driver-class-name:org.h2.Driver}' == 'org.h2.Driver'")
@Order(7)
public class SchemaCapsuleQuotaInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SchemaCapsuleQuotaInitializer.class);

    @Autowired
    private JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        createTableIfAbsent();
    }

    private void createTableIfAbsent() {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE upper(table_name) = 'TB_CAPSULE_USAGE_QUOTA' AND table_schema = SCHEMA()",
                    Integer.class);
            if (count != null && count > 0) {
                log.debug("tb_capsule_usage_quota already exists — skipped");
                return;
            }
        } catch (Exception e) {
            log.debug("information_schema probe failed ({}); proceeding with CREATE TABLE IF NOT EXISTS", e.getMessage());
        }
        try {
            jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS tb_capsule_usage_quota (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      visitor_user_id BIGINT NOT NULL,
                      capsule_id BIGINT NOT NULL,
                      quota_date DATE NOT NULL,
                      turn_count INT DEFAULT 0,
                      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                      UNIQUE KEY uk_quota (visitor_user_id, capsule_id, quota_date)
                    )
                    """);
            log.info("Schema migration applied: created tb_capsule_usage_quota");
        } catch (Exception e) {
            log.warn("Could not create tb_capsule_usage_quota (may already exist): {}", e.getMessage());
        }
    }
}
