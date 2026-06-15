package com.innercosmos.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VS-006: verifies the UNIQUE (user_id, record_date) constraint on
 * tb_daily_record rejects a duplicate per user/day, and that the additive
 * {@link SchemaM5Initializer} migration is idempotent (re-running it against a
 * database that already has the constraint does not fail).
 *
 * Runs against the in-memory H2 (MODE=MySQL) test datasource, where schema.sql
 * provisions the constraint for fresh installs.
 */
@SpringBootTest(properties = {
        "llm.mode=dev",
        "llm.provider=mock",
        "llm.allow-fallback=true"
})
class DailyRecordUniqueConstraintTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SchemaM5Initializer m5Initializer;

    private Long seedUser() {
        // Use a unique username per run to avoid colliding with seeded demo accounts.
        String username = "du-test-" + System.nanoTime();
        jdbc.update("INSERT INTO tb_user (username, password_hash, role, status) VALUES (?, ?, ?, ?)",
                username, "hash", "USER", "ACTIVE");
        return jdbc.queryForObject("SELECT id FROM tb_user WHERE username = ?", Long.class, username);
    }

    @Test
    @DisplayName("First daily record for a user/day persists")
    void firstRecordForUserDay_persists() {
        Long user = seedUser();
        LocalDate today = LocalDate.now();

        jdbc.update("INSERT INTO tb_daily_record (user_id, record_date, status) VALUES (?, ?, ?)",
                user, today, "ACTIVE");

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_daily_record WHERE user_id = ? AND record_date = ?",
                Integer.class, user, today);
        assertNotNull(count);
        assertTrue(count >= 1);
    }

    @Test
    @DisplayName("Duplicate (user_id, record_date) is rejected by the unique constraint")
    void duplicateUserDate_rejected() {
        Long user = seedUser();
        LocalDate today = LocalDate.now();

        jdbc.update("INSERT INTO tb_daily_record (user_id, record_date, status) VALUES (?, ?, ?)",
                user, today, "ACTIVE");

        // A second row for the same user/day must be rejected.
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("INSERT INTO tb_daily_record (user_id, record_date, status) VALUES (?, ?, ?)",
                        user, today, "ACTIVE"));
    }

    @Test
    @DisplayName("Different users on the same day each get their own row")
    void differentUsers_sameDay_bothPersist() {
        Long userA = seedUser();
        Long userB = seedUser();
        LocalDate today = LocalDate.now();

        jdbc.update("INSERT INTO tb_daily_record (user_id, record_date, status) VALUES (?, ?, ?)",
                userA, today, "ACTIVE");
        jdbc.update("INSERT INTO tb_daily_record (user_id, record_date, status) VALUES (?, ?, ?)",
                userB, today, "ACTIVE");

        Integer countA = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_daily_record WHERE user_id = ?", Integer.class, userA);
        Integer countB = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_daily_record WHERE user_id = ?", Integer.class, userB);
        assertNotNull(countA);
        assertNotNull(countB);
        assertTrue(countA >= 1);
        assertTrue(countB >= 1);
    }

    @Test
    @DisplayName("SchemaM5Initializer is idempotent: re-running does not fail")
    void m5Initializer_idempotent() {
        // The Spring context already ran the initializer once at startup.
        // Re-invoking it manually must not throw.
        m5Initializer.run(null);

        // And the constraint must still be present.
        Integer existing = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE upper(table_name) = 'TB_DAILY_RECORD'
                  AND upper(constraint_type) = 'UNIQUE'
                  AND upper(constraint_name) = 'UK_DAILY_RECORD_USER_DATE'
                """, Integer.class);
        assertNotNull(existing);
        assertTrue(existing > 0, "Unique constraint must exist after migration");
    }
}
