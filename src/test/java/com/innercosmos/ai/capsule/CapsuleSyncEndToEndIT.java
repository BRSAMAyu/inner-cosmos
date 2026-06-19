package com.innercosmos.ai.capsule;

import com.innercosmos.ai.portrait.UserPortraitService;
import com.innercosmos.ai.portrait.dto.PortraitDeltas;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IC-CAP-002 FIX-4: end-to-end proof that the capsule-sync chain is genuinely wired through
 * the REAL production publish site. Unlike {@link CapsuleSyncWiringIT} (which calls
 * onPortraitOrRelationshipChanged directly) and {@link com.innercosmos.event.CapsuleRegenerateListenerTest}
 * (which calls the listener method directly), this test calls
 * {@link UserPortraitService#applyDeltas} — the exact method production runs — and then awaits a
 * PENDING tb_capsule_sync_queue row appearing, proving:
 *   1. applyDeltas actually publishes CapsuleSyncTriggerEvent, AND
 *   2. the @Async @TransactionalEventListener(AFTER_COMMIT) listener receives it and drives the sync.
 *
 * Why NOT @Transactional: the listener is now AFTER_COMMIT (FIX-2). A @Transactional test never
 * commits (Spring rolls it back), so the listener would never fire. We therefore commit for real
 * via applyDeltas's own @Transactional boundary and clean up the seeded rows in @AfterEach.
 *
 * @EnableAsync is active via ThreadPoolConfig (taskExecutor), so the listener runs on the pool —
 * we await the terminal effect with a bounded poll loop (no Awaitility dependency in the repo).
 */
@SpringBootTest(properties = {
        "llm.mode=dev",
        "llm.provider=mock",
        "llm.allow-fallback=true"
})
class CapsuleSyncEndToEndIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserPortraitService userPortraitService;

    private Long userId;
    private Long capsuleId;

    private Long seedUser() {
        String username = "e2e-sync-" + System.nanoTime();
        jdbc.update("INSERT INTO tb_user (username, password_hash, role, status) VALUES (?, ?, ?, ?)",
                username, "hash", "USER", "ACTIVE");
        return jdbc.queryForObject("SELECT id FROM tb_user WHERE username = ?", Long.class, username);
    }

    private Long seedUserCapsule(Long ownerId) {
        jdbc.update(
                "INSERT INTO tb_echo_capsule (owner_user_id, capsule_type, pseudonym, intro, "
                        + "visibility_status, is_public, conversation_limit_per_day) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                ownerId, "USER_CAPSULE", "e2e-echo", "e2e intro",
                "PUBLIC", true, 10);
        return jdbc.queryForObject(
                "SELECT id FROM tb_echo_capsule WHERE owner_user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, ownerId);
    }

    private long pendingCount() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_capsule_sync_queue WHERE user_id = ? AND capsule_id = ? AND status = 'PENDING'",
                Long.class, userId, capsuleId);
    }

    @AfterEach
    void cleanup() {
        // This test commits for real (AFTER_COMMIT listener), so tear down the rows it created.
        if (userId != null) {
            jdbc.update("DELETE FROM tb_capsule_sync_queue WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM tb_user_portrait WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM tb_user_portrait_history WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM tb_notification WHERE user_id = ?", userId);
            if (capsuleId != null) {
                jdbc.update("DELETE FROM tb_echo_capsule WHERE id = ?", capsuleId);
            }
            jdbc.update("DELETE FROM tb_user WHERE id = ?", userId);
        }
    }

    @Test
    @DisplayName("FIX-4: applyDeltas (real production publish site) -> AFTER_COMMIT async listener -> PENDING row appears")
    void applyDeltas_publishesAndReachesListener_endToEnd() throws InterruptedException {
        userId = seedUser();
        capsuleId = seedUserCapsule(userId);

        assertEquals(0, pendingCount(), "no PENDING row before applyDeltas");

        // Call the EXACT production method. Its @Transactional commit fires the AFTER_COMMIT listener,
        // which runs @Async and drives onPortraitOrRelationshipChanged -> inserts a PENDING row.
        List<PortraitDeltas.Delta> deltas = List.of(
                new PortraitDeltas.Delta("values", "{\"v\":\"真实\"}", 0.8, 0.7, List.of("t1")));
        userPortraitService.applyDeltas(userId, deltas);

        // Bounded poll for the terminal effect (async, post-commit). ~5s budget.
        long deadline = System.currentTimeMillis() + 5_000;
        long pending = 0;
        while (System.currentTimeMillis() < deadline) {
            pending = pendingCount();
            if (pending >= 1) break;
            Thread.sleep(50);
        }

        assertEquals(1, pending,
                "applyDeltas must publish CapsuleSyncTriggerEvent AND the AFTER_COMMIT async listener "
                        + "must drive the sync, producing exactly one PENDING row end-to-end");
    }
}
