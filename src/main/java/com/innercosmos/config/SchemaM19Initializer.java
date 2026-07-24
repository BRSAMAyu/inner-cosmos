package com.innercosmos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Long-lived non-Flyway H2 databases converge on V23's per-user TTS voice + Aurora "inner voice"
 * preference columns on tb_user_profile.
 *
 * <p>2026-07-24 8-agent audit follow-up: reproduced live against a real-provider demo run --
 * {@code AuroraAgentServiceImpl.loadProfile()} selects these columns unconditionally, so any
 * pre-existing file-backed H2 database created before V23 merged (i.e. exactly the situation on an
 * operator's own long-running local machine, the delivery scenario this audit covers) 500s on the
 * very first Aurora chat message with {@code Column "preferred_tts_voice_id" not found}. Every
 * other Flyway migration through V22 already has a matching retrofit initializer in this package;
 * V23 was simply missing one.
 */
@Component
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "false", matchIfMissing = true)
@ConditionalOnExpression("'${spring.datasource.driver-class-name:org.h2.Driver}' == 'org.h2.Driver'")
@Order(19)
public class SchemaM19Initializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SchemaM19Initializer.class);

    private final JdbcTemplate jdbc;

    public SchemaM19Initializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(ApplicationArguments args) {
        migrate("tb_user_profile", "preferred_tts_voice_id VARCHAR(64)");
        migrate("tb_user_profile", "inner_voice_enabled BOOLEAN DEFAULT TRUE");
        migrate("tb_user_profile", "inner_voice_mode VARCHAR(16) DEFAULT 'AMBIENT'");
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
