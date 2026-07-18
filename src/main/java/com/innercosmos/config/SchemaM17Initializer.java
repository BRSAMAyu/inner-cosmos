package com.innercosmos.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Long-lived non-Flyway databases converge on the v1 boundary compare-and-set token. */
@Component
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "false", matchIfMissing = true)
@ConditionalOnExpression("'${spring.datasource.driver-class-name:org.h2.Driver}' == 'org.h2.Driver'")
@Order(17)
public class SchemaM17Initializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public SchemaM17Initializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbc.execute("ALTER TABLE tb_capsule_boundary ADD COLUMN version INT NOT NULL DEFAULT 1");
        } catch (Exception alreadyPresent) {
            try {
                jdbc.queryForList("SELECT version FROM tb_capsule_boundary WHERE 1=0");
            } catch (Exception missing) {
                throw new IllegalStateException("boundary version migration failed", alreadyPresent);
            }
        }
    }
}
