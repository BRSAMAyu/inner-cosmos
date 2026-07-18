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
 * IC-EMO-001 schema migration: enrich tb_emotion_trace with the emotion spectrum
 * and analysis-source columns.
 *
 * <p>Mirrors {@link SchemaM5Initializer}: probes information_schema.columns and
 * only issues a guarded {@code ALTER TABLE ... ADD COLUMN} when a column is
 * absent (H2 in MODE=MySQL does not support {@code ADD COLUMN IF NOT EXISTS}
 * portably). Existing rows keep NULL for the new columns (backward compatible),
 * and any residual failure is caught and logged so startup is never blocked.
 */
@Component
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "false", matchIfMissing = true)
@ConditionalOnExpression("'${spring.datasource.driver-class-name:org.h2.Driver}' == 'org.h2.Driver'")
@Order(6)
public class SchemaEmotionSpectrumInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SchemaEmotionSpectrumInitializer.class);

    private static final String TABLE = "tb_emotion_trace";

    @Autowired
    private JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        addColumnIfAbsent("emotion_spectrum", "TEXT");
        addColumnIfAbsent("analysis_source", "VARCHAR(32)");
    }

    private void addColumnIfAbsent(String column, String type) {
        try {
            Integer existing = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE upper(table_name) = ?
                      AND upper(column_name) = ?
                    """, Integer.class, TABLE.toUpperCase(), column.toUpperCase());
            if (existing != null && existing > 0) {
                log.debug("Column {}.{} already exists — skipped", TABLE, column);
                return;
            }
            guardedAlter(column, type);
        } catch (Exception e) {
            // information_schema probe itself failed — fall back to a guarded ALTER.
            log.debug("information_schema probe failed for {}.{} ({}); attempting guarded ALTER",
                    TABLE, column, e.getMessage());
            guardedAlter(column, type);
        }
    }

    private void guardedAlter(String column, String type) {
        String ddl = "ALTER TABLE " + TABLE + " ADD COLUMN " + column + " " + type;
        try {
            jdbc.execute(ddl);
            log.info("Schema migration applied: {}", ddl);
        } catch (Exception inner) {
            // Column may already exist under a slightly different metadata
            // signature — safe to surface as a warn; startup must not break.
            log.warn("Could not apply column {}.{} (may already exist): {}",
                    TABLE, column, inner.getMessage());
        }
    }
}
