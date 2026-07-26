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
 * H2/local equivalent of PostgreSQL V26 for long-lived development databases.
 *
 * <p>Fresh databases already contain these columns through {@code schema.sql}. Existing file-backed
 * H2 databases do not run Flyway, so every alteration is independently idempotent and deliberately
 * limited to the Flyway-disabled H2 profile.</p>
 */
@Component
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "false", matchIfMissing = true)
@ConditionalOnExpression("'${spring.datasource.driver-class-name:org.h2.Driver}' == 'org.h2.Driver'")
@Order(26)
public class SchemaAuroraActionInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SchemaAuroraActionInitializer.class);

    private final JdbcTemplate jdbc;

    public SchemaAuroraActionInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        addColumn("proposed_action_type VARCHAR(48)");
        addColumn("proposed_action_payload TEXT");
        addColumn("proposed_action_summary VARCHAR(500)");
        addColumn("action_status VARCHAR(32)");
        addColumn("action_confirmed_at TIMESTAMP");
        addColumn("action_result_ref VARCHAR(160)");
        try {
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_turn_plan_pending_action "
                    + "ON tb_turn_plan(user_id, action_status, id)");
        } catch (Exception existsOrUnsupported) {
            log.debug("Aurora action pending index already exists or is unsupported: {}",
                    existsOrUnsupported.getMessage());
        }
    }

    private void addColumn(String definition) {
        String ddl = "ALTER TABLE tb_turn_plan ADD COLUMN " + definition;
        try {
            jdbc.execute(ddl);
            log.info("Schema migration applied: {}", ddl);
        } catch (Exception exists) {
            log.debug("Schema migration skipped for {}: {}", ddl, exists.getMessage());
        }
    }
}
