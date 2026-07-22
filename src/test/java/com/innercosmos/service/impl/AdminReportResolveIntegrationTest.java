package com.innercosmos.service.impl;

import com.innercosmos.service.AdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gemini audit / remaining-work-handoff.md 2.2.5: no test anywhere exercised AdminService
 * #resolveReport against a real, freshly-created pending report -- confirmed absent by
 * `grep -rl "resolveReport(" ./src/test` before this file existed. Seeds a real
 * tb_report_record row via JdbcTemplate, resolves it through the real service against H2,
 * and verifies both the status transition and the audit-log write land in the database, not
 * just that the method returns without throwing.
 */
@SpringBootTest(properties = { "llm.mode=dev", "llm.provider=mock", "llm.allow-fallback=true" })
@Transactional
class AdminReportResolveIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AdminService adminService;

    private Long seedUser(String usernamePrefix) {
        String username = usernamePrefix + "-" + System.nanoTime();
        jdbc.update("INSERT INTO tb_user (username, password_hash, role, status) VALUES (?, ?, ?, ?)",
                username, "hash", "USER", "ACTIVE");
        return jdbc.queryForObject("SELECT id FROM tb_user WHERE username = ?", Long.class, username);
    }

    private Long seedPendingReport(Long reporterUserId, String targetType, Long targetId, String reason) {
        jdbc.update("INSERT INTO tb_report_record (reporter_user_id, target_type, target_id, reason, status) " +
                        "VALUES (?, ?, ?, ?, 'PENDING')",
                reporterUserId, targetType, targetId, reason);
        return jdbc.queryForObject(
                "SELECT id FROM tb_report_record WHERE reporter_user_id = ? AND target_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, reporterUserId, targetId);
    }

    @Test
    void resolvingARealPendingReportUpdatesItsStatusAndWritesAnAuditLog() {
        Long admin = seedUser("admin-it");
        Long reporter = seedUser("reporter-it");
        Long target = seedUser("target-it");
        Long reportId = seedPendingReport(reporter, "USER", target, "骚扰行为");

        adminService.resolveReport(admin, reportId, "dismiss", "已核实，无需处理");

        String status = jdbc.queryForObject("SELECT status FROM tb_report_record WHERE id = ?", String.class, reportId);
        assertEquals("RESOLVED_DISMISS", status);

        Integer auditCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_admin_action_log WHERE admin_user_id = ? AND action_type = ? AND target_id = ?",
                Integer.class, admin, "RESOLVE_REPORT_DISMISS", target);
        assertEquals(1, auditCount, "resolving a report must leave exactly one audit-log receipt");
    }

    @Test
    void resolvingAnAlreadyMissingReportFailsFast() {
        Long admin = seedUser("admin-it2");
        Long missingId = 999_999_999L;

        assertThrows(RuntimeException.class,
                () -> adminService.resolveReport(admin, missingId, "dismiss", "n/a"));
    }
}
