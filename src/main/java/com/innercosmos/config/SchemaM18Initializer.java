package com.innercosmos.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Long-lived non-Flyway H2 databases converge on the data-retraction receipt audit table. */
@Component
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "false", matchIfMissing = true)
@ConditionalOnExpression("'${spring.datasource.driver-class-name:org.h2.Driver}' == 'org.h2.Driver'")
@Order(18)
public class SchemaM18Initializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public SchemaM18Initializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(ApplicationArguments args) {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS tb_data_retraction_receipt (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  user_id BIGINT NOT NULL,
                  subject_type VARCHAR(32) NOT NULL,
                  subject_id BIGINT NOT NULL,
                  derivative_type VARCHAR(48) NOT NULL,
                  action VARCHAR(24) NOT NULL,
                  affected_count INT NOT NULL DEFAULT 0,
                  reason VARCHAR(240),
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  INDEX idx_data_retraction_user (user_id, created_at),
                  INDEX idx_data_retraction_subject (subject_type, subject_id)
                )
                """);
    }
}
