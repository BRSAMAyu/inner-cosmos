package com.innercosmos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * M-008 (Phase-5): retrofit the UNIQUE(user_id, source_session_id) constraint onto
 * tb_memory_card for EXISTING databases. The fresh-DB constraint lives in schema.sql, but
 * CREATE TABLE IF NOT EXISTS + sql.init.mode=always means it is never applied to an already-
 * existing table. This runner adds it idempotently; if pre-M-007 duplicate rows block the
 * constraint, it logs and skips (M-007's atomic finish already prevents new duplicates).
 */
@Component
@Order(8)
public class SchemaMemoryCardUniqueInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SchemaMemoryCardUniqueInitializer.class);

    @Autowired
    private JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        if (constraintExists()) {
            log.debug("uk_memory_card_user_session already exists — skipped");
            return;
        }
        try {
            jdbc.execute("ALTER TABLE tb_memory_card ADD CONSTRAINT uk_memory_card_user_session UNIQUE (user_id, source_session_id)");
            log.info("Schema migration applied: uk_memory_card_user_session UNIQUE on tb_memory_card");
        } catch (Exception e) {
            // Most likely pre-M-007 duplicate (user_id, source_session_id) rows; M-007 prevents
            // new ones. Log and continue rather than failing startup.
            log.warn("Could not add uk_memory_card_user_session (existing duplicate rows?): {}", e.getMessage());
        }
    }

    private boolean constraintExists() {
        try {
            Integer c = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.table_constraints "
                            + "WHERE upper(constraint_name) = 'UK_MEMORY_CARD_USER_SESSION' AND table_schema = SCHEMA()",
                    Integer.class);
            return c != null && c > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
