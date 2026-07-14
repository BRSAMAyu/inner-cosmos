package com.innercosmos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * IC-LTR-001 schema migration (H2 + MySQL compatible, guarded):
 *  - tb_slow_letter + thread_id  (links a letter to its {@code tb_letter_thread} conversation)
 *
 * Without this column replies were orphaned: every reply spawned a fresh thread and there
 * was no way to walk a back-and-forth conversation. Guarded by an information_schema probe
 * and wrapped in try/catch, mirroring {@link SchemaCapsuleEnergyInitializer}.
 */
@Component
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "false", matchIfMissing = true)
@Order(9)
public class SchemaLetterThreadInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SchemaLetterThreadInitializer.class);

    @Autowired
    private JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        addColumnIfAbsent("TB_SLOW_LETTER", "THREAD_ID",
                "ALTER TABLE tb_slow_letter ADD COLUMN thread_id BIGINT NULL");
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
}
