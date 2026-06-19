package com.innercosmos.ai.capsule;

import com.innercosmos.entity.CapsuleSyncQueue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IC-CAP-002 B-1: proves the sync chain's terminal effect (a PENDING
 * tb_capsule_sync_queue row appears for a USER_CAPSULE owned by the user)
 * and that repeated triggers DEDUPE onto a single PENDING row rather than
 * storming the queue.
 *
 * RED-before evidence: prior to this work, onPortraitOrRelationshipChanged()
 * had ZERO production callers and inserted a brand new PENDING row on every
 * invocation (no dedup), so the dedup assertion below failed.
 */
@SpringBootTest(properties = {
        "llm.mode=dev",
        "llm.provider=mock",
        "llm.allow-fallback=true"
})
class CapsuleSyncWiringIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CapsuleSyncService syncService;

    private Long seedUser() {
        String username = "sync-wire-" + System.nanoTime();
        jdbc.update("INSERT INTO tb_user (username, password_hash, role, status) VALUES (?, ?, ?, ?)",
                username, "hash", "USER", "ACTIVE");
        return jdbc.queryForObject("SELECT id FROM tb_user WHERE username = ?", Long.class, username);
    }

    private Long seedUserCapsule(Long ownerId) {
        jdbc.update(
                "INSERT INTO tb_echo_capsule (owner_user_id, capsule_type, pseudonym, intro, "
                        + "visibility_status, is_public, conversation_limit_per_day) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                ownerId, "USER_CAPSULE", "wire-echo", "wire intro",
                "PUBLIC", true, 10);
        return jdbc.queryForObject(
                "SELECT id FROM tb_echo_capsule WHERE owner_user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, ownerId);
    }

    private long pendingCount(Long userId, Long capsuleId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_capsule_sync_queue WHERE user_id = ? AND capsule_id = ? AND status = 'PENDING'",
                Long.class, userId, capsuleId);
    }

    @Test
    @DisplayName("B-1: onPortraitOrRelationshipChanged creates exactly one PENDING row for a USER_CAPSULE")
    @Transactional
    void syncTrigger_createsPendingRow() {
        Long userId = seedUser();
        Long capsuleId = seedUserCapsule(userId);

        assertEquals(0, pendingCount(userId, capsuleId), "no PENDING row before trigger");

        syncService.onPortraitOrRelationshipChanged(userId);

        assertEquals(1, pendingCount(userId, capsuleId),
                "exactly one PENDING row must appear after the trigger fires");

        List<CapsuleSyncQueue> pending = syncService.pending(userId);
        assertTrue(pending.stream().anyMatch(q -> capsuleId.equals(q.capsuleId) && "PENDING".equals(q.status)),
                "pending() must surface the new PENDING row");
    }

    @Test
    @DisplayName("B-1: repeated triggers dedupe onto a single PENDING row (no storm)")
    @Transactional
    void syncTrigger_dedupesMultipleChanges() {
        Long userId = seedUser();
        Long capsuleId = seedUserCapsule(userId);

        syncService.onPortraitOrRelationshipChanged(userId);
        syncService.onPortraitOrRelationshipChanged(userId);
        syncService.onPortraitOrRelationshipChanged(userId);

        assertEquals(1, pendingCount(userId, capsuleId),
                "three triggers for the same user+capsule must collapse to a single PENDING row");
    }
}
