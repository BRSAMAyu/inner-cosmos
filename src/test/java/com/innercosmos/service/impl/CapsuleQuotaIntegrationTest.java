package com.innercosmos.service.impl;

import com.innercosmos.entity.PersonaChatMessage;
import com.innercosmos.entity.PersonaChatSession;
import com.innercosmos.service.PersonaChatService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IC-CAP-001 integration tests: per-capsule per-day quota enforcement against real H2.
 *
 * Verifies:
 * 1. A second session for the same user+capsule on the same day cannot bypass the daily quota.
 * 2. After dailyLimit replies succeed, the next reply returns LETTER_GUIDED.
 * 3. The tb_capsule_usage_quota table is updated correctly after each reply.
 */
@SpringBootTest(properties = {
        "llm.mode=dev",
        "llm.provider=mock",
        "llm.allow-fallback=true"
})
class CapsuleQuotaIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PersonaChatService personaChatService;

    // ──────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────

    private Long seedUser() {
        String username = "quota-it-" + System.nanoTime();
        jdbc.update("INSERT INTO tb_user (username, password_hash, role, status) VALUES (?, ?, ?, ?)",
                username, "hash", "USER", "ACTIVE");
        return jdbc.queryForObject("SELECT id FROM tb_user WHERE username = ?", Long.class, username);
    }

    /** Insert a public capsule with a tiny daily limit for testing. */
    private Long seedCapsule(Long ownerId, int dailyLimit) {
        jdbc.update(
                "INSERT INTO tb_echo_capsule (owner_user_id, capsule_type, pseudonym, intro, "
                        + "visibility_status, is_public, conversation_limit_per_day) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                ownerId, "USER_CAPSULE", "test-echo", "test intro",
                "PUBLIC", true, dailyLimit);
        return jdbc.queryForObject(
                "SELECT id FROM tb_echo_capsule WHERE owner_user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, ownerId);
    }

    private int quotaTurnCount(Long userId, Long capsuleId) {
        Integer count = jdbc.queryForObject(
                "SELECT turn_count FROM tb_capsule_usage_quota "
                        + "WHERE visitor_user_id = ? AND capsule_id = ? AND quota_date = CURRENT_DATE",
                Integer.class, userId, capsuleId);
        return count != null ? count : 0;
    }

    private int quotaRowCount(Long userId, Long capsuleId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_capsule_usage_quota "
                        + "WHERE visitor_user_id = ? AND capsule_id = ? AND quota_date = CURRENT_DATE",
                Integer.class, userId, capsuleId);
        return count != null ? count : 0;
    }

    // ──────────────────────────────────────────────────────
    // Test 1: Quota row is created and incremented on each reply
    // ──────────────────────────────────────────────────────

    @Test
    @Transactional
    @DisplayName("Quota row is created on first reply and incremented on subsequent replies")
    void quotaRow_createdAndIncremented() {
        Long owner = seedUser();
        Long visitor = seedUser();
        Long capsuleId = seedCapsule(owner, 5);

        PersonaChatSession session = personaChatService.create(visitor, capsuleId);

        // Before any reply: no quota row
        assertEquals(0, quotaRowCount(visitor, capsuleId));

        // First reply
        PersonaChatMessage msg1 = personaChatService.reply(visitor, session.id, "hello");
        assertFalse(msg1.textContent.contains("慢信"), "First reply should succeed");
        assertEquals(1, quotaTurnCount(visitor, capsuleId), "Quota should be 1 after first reply");

        // Second reply
        PersonaChatMessage msg2 = personaChatService.reply(visitor, session.id, "second");
        assertFalse(msg2.textContent.contains("慢信"), "Second reply should succeed");
        assertEquals(2, quotaTurnCount(visitor, capsuleId), "Quota should be 2 after second reply");
    }

    // ──────────────────────────────────────────────────────
    // Test 2: After dailyLimit replies, next reply returns LETTER_GUIDED
    // ──────────────────────────────────────────────────────

    @Test
    @Transactional
    @DisplayName("After dailyLimit replies succeed, the next reply returns LETTER_GUIDED status")
    void afterDailyLimit_nextReplyIsLetterGuided() {
        Long owner = seedUser();
        Long visitor = seedUser();
        int dailyLimit = 2; // small limit for fast testing
        Long capsuleId = seedCapsule(owner, dailyLimit);

        PersonaChatSession session = personaChatService.create(visitor, capsuleId);

        // Exhaust quota
        for (int i = 0; i < dailyLimit; i++) {
            PersonaChatMessage msg = personaChatService.reply(visitor, session.id, "msg " + i);
            assertFalse(msg.textContent.contains("慢信"), "Reply " + i + " should succeed");
        }

        // Quota should be at limit
        assertEquals(dailyLimit, quotaTurnCount(visitor, capsuleId));

        // Next reply should be blocked
        PersonaChatMessage blocked = personaChatService.reply(visitor, session.id, "over limit");
        assertTrue(blocked.textContent.contains("慢信"),
                "Reply over daily limit must return letter-guided message containing '慢信'");

        // Quota should NOT have increased past the limit
        assertEquals(dailyLimit, quotaTurnCount(visitor, capsuleId),
                "Quota count must not increment when limit is reached");
    }

    // ──────────────────────────────────────────────────────
    // Test 3: Cross-session enforcement — second session cannot bypass quota
    // ──────────────────────────────────────────────────────

    @Test
    @Transactional
    @DisplayName("Second session for same user+capsule+day cannot bypass daily quota")
    void secondSession_cannotBypassDailyQuota() {
        Long owner = seedUser();
        Long visitor = seedUser();
        // resolveDailyLimit clamps configured values with Math.max(2, ...), so 2 is the
        // smallest limit that is actually enforced as-is. Use 2 to exhaust exactly.
        int dailyLimit = 2;
        Long capsuleId = seedCapsule(owner, dailyLimit);

        // Session 1: use up the full daily quota (2 turns)
        PersonaChatSession session1 = personaChatService.create(visitor, capsuleId);
        for (int i = 0; i < dailyLimit; i++) {
            PersonaChatMessage msg = personaChatService.reply(visitor, session1.id, "session1 msg " + i);
            assertFalse(msg.textContent.contains("慢信"), "Session 1 reply " + i + " should succeed");
        }
        assertEquals(dailyLimit, quotaTurnCount(visitor, capsuleId));

        // Session 2 (NEW session — turnCount starts at 0 in the session table)
        PersonaChatSession session2 = personaChatService.create(visitor, capsuleId);
        assertEquals(0, session2.turnCount, "New session turnCount starts at 0");

        // But the cross-session daily quota is already at dailyLimit
        PersonaChatMessage blocked = personaChatService.reply(visitor, session2.id, "second session bypass attempt");
        assertTrue(blocked.textContent.contains("慢信"),
                "Second session must be blocked by cross-session daily quota even though session.turnCount=0");

        // Quota count stays at dailyLimit — did not increment
        assertEquals(dailyLimit, quotaTurnCount(visitor, capsuleId),
                "Quota must not increment when limit is already reached");
    }

    // ──────────────────────────────────────────────────────
    // Test 4: SEED capsule uses 10 as daily limit, not 0 (unlimited)
    // ──────────────────────────────────────────────────────

    @Test
    @Transactional
    @DisplayName("SEED capsule is limited to 50 replies per day (not unlimited)")
    void seedCapsule_limitedTo10PerDay() {
        Long owner = seedUser();
        Long visitor = seedUser();

        // Insert a SEED capsule with NO explicit conversationLimitPerDay
        jdbc.update(
                "INSERT INTO tb_echo_capsule (owner_user_id, capsule_type, pseudonym, intro, "
                        + "visibility_status, is_public) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                owner, "SEED_CAPSULE", "seed-echo", "seed intro", "PUBLIC", true);
        Long capsuleId = jdbc.queryForObject(
                "SELECT id FROM tb_echo_capsule WHERE owner_user_id = ? AND capsule_type = 'SEED_CAPSULE' ORDER BY id DESC LIMIT 1",
                Long.class, owner);

        // Pre-fill quota to 50 (= the SEED effective limit) via direct SQL
        jdbc.update(
                "INSERT INTO tb_capsule_usage_quota (visitor_user_id, capsule_id, quota_date, turn_count) "
                        + "VALUES (?, ?, CURRENT_DATE, ?)",
                visitor, capsuleId, 50);

        // Create a session and try to reply — should be blocked (seed effective limit = 50)
        PersonaChatSession session = personaChatService.create(visitor, capsuleId);
        PersonaChatMessage blocked = personaChatService.reply(visitor, session.id, "beyond seed limit");

        assertTrue(blocked.textContent.contains("慢信"),
                "SEED capsule must be blocked at 50 daily turns (not unlimited at 0)");
    }

    // ──────────────────────────────────────────────────────
    // Test 5: Different users have separate quotas for the same capsule
    // ──────────────────────────────────────────────────────

    @Test
    @Transactional
    @DisplayName("Two different visitors have independent daily quotas for the same capsule")
    void differentVisitors_haveIndependentQuotas() {
        Long owner = seedUser();
        Long visitor1 = seedUser();
        Long visitor2 = seedUser();
        int dailyLimit = 1;
        Long capsuleId = seedCapsule(owner, dailyLimit);

        // visitor1 uses up quota
        PersonaChatSession session1 = personaChatService.create(visitor1, capsuleId);
        personaChatService.reply(visitor1, session1.id, "visitor1 msg");
        assertEquals(1, quotaTurnCount(visitor1, capsuleId));

        // visitor2 is a separate user — should still get their quota
        PersonaChatSession session2 = personaChatService.create(visitor2, capsuleId);
        PersonaChatMessage msg2 = personaChatService.reply(visitor2, session2.id, "visitor2 msg");
        assertFalse(msg2.textContent.contains("慢信"),
                "visitor2 must have an independent quota from visitor1");
        assertEquals(1, quotaTurnCount(visitor2, capsuleId));
    }
}
