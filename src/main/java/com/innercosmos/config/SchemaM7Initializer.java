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
 * Schema migration M7: isolated Simulator capability contract (对齐文档/16 Campaign C).
 *
 * Adds simulator_only to tb_echo_capsule for an existing H2 database. Fresh installs
 * already get it from schema.sql. H2 does not support ADD COLUMN IF NOT EXISTS, so the
 * statement is attempted and any "column already exists" failure is ignored.
 */
@Component
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "false", matchIfMissing = true)
@ConditionalOnExpression("'${spring.datasource.driver-class-name:org.h2.Driver}' == 'org.h2.Driver'")
@Order(7)
public class SchemaM7Initializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SchemaM7Initializer.class);

    @Autowired
    private JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        migrate("tb_echo_capsule", "simulator_only BOOLEAN DEFAULT FALSE");
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
