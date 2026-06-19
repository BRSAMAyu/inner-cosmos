package com.innercosmos.service.impl;

import com.innercosmos.event.DialogFinishedEvent;
import com.innercosmos.event.EmotionTraceListener;
import com.innercosmos.service.MemoryService;
import com.innercosmos.service.MemorySettlementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IC-EMO-001 integration: proves the de-dup core.
 *
 * 1. A dialog-finish event writes EXACTLY one enriched EmotionTrace for the session.
 * 2. Settling the SAME session upserts that row (still exactly one) and upgrades
 *    analysisSource to SETTLEMENT with a non-null spectrum — no double write.
 * 3. Settling a diary inserts a null-session trace.
 *
 * Runs against the in-memory H2 (MODE=MySQL) test datasource in deterministic
 * mock mode.
 */
@SpringBootTest(properties = {
        "llm.mode=dev",
        "llm.provider=mock",
        "llm.allow-fallback=true"
})
class EmotionTraceDedupIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EmotionTraceListener emotionTraceListener;
    @Autowired
    private MemorySettlementService memorySettlementService;
    @Autowired
    private MemoryService memoryService;

    private Long seedUser() {
        String username = "emo-it-" + System.nanoTime();
        jdbc.update("INSERT INTO tb_user (username, password_hash, role, status) VALUES (?, ?, ?, ?)",
                username, "hash", "USER", "ACTIVE");
        return jdbc.queryForObject("SELECT id FROM tb_user WHERE username = ?", Long.class, username);
    }

    private Long seedSession(Long userId) {
        jdbc.update("INSERT INTO tb_dialog_session (user_id, title, session_type, status, message_count, token_estimate) "
                + "VALUES (?, ?, ?, ?, ?, ?)", userId, "测试会话", "AURORA_CHAT", "ACTIVE", 1, 0);
        return jdbc.queryForObject(
                "SELECT id FROM tb_dialog_session WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, userId);
    }

    private void seedUserMessage(Long sessionId, String text) {
        jdbc.update("INSERT INTO tb_dialog_message (session_id, speaker, text_content) VALUES (?, ?, ?)",
                sessionId, "USER", text);
    }

    private int traceCount(Long userId, Long sessionId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_emotion_trace WHERE user_id = ? AND source_session_id = ?",
                Integer.class, userId, sessionId);
    }

    /** Poll until the async listener has written its trace (or timeout). */
    private void awaitTrace(Long userId, Long sessionId) {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(5).toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (traceCount(userId, sessionId) >= 1) return;
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Test
    @DisplayName("Dialog finish writes exactly one enriched trace; settling same session upserts (still one)")
    void dialogThenSettle_noDoubleWrite() {
        Long userId = seedUser();
        Long sessionId = seedSession(userId);
        seedUserMessage(sessionId, "今天作业堆得太多了，我很焦虑也很累，撑不住了。");

        // 1. Dialog finish -> exactly one enriched trace.
        emotionTraceListener.onDialogFinished(new DialogFinishedEvent(userId, sessionId));
        awaitTrace(userId, sessionId);
        assertEquals(1, traceCount(userId, sessionId), "listener must write exactly one trace");

        Map<String, Object> afterListener = jdbc.queryForMap(
                "SELECT analysis_source, emotion_spectrum, emotion_name FROM tb_emotion_trace "
                        + "WHERE user_id = ? AND source_session_id = ?", userId, sessionId);
        assertEquals("LEXICON", afterListener.get("analysis_source"),
                "mock/lexicon path tags the listener trace as LEXICON");
        assertNotNull(afterListener.get("emotion_spectrum"), "listener trace must carry a spectrum");

        // 2. Settle the SAME session -> upsert, still exactly one row, source upgraded.
        memorySettlementService.settleSession(userId, sessionId);
        assertEquals(1, traceCount(userId, sessionId),
                "settling the same session must UPSERT, not double-write");

        Map<String, Object> afterSettle = jdbc.queryForMap(
                "SELECT analysis_source, emotion_spectrum FROM tb_emotion_trace "
                        + "WHERE user_id = ? AND source_session_id = ?", userId, sessionId);
        assertEquals("SETTLEMENT", afterSettle.get("analysis_source"),
                "settlement must upgrade the source on the SAME row");
        assertNotNull(afterSettle.get("emotion_spectrum"), "settled trace must carry a spectrum");
    }

    @Test
    @DisplayName("Both DialogFinished listeners on same session -> exactly one enriched trace (no double-write)")
    void bothListeners_sameSession_noDoubleWrite() {
        Long userId = seedUser();
        Long sessionId = seedSession(userId);
        seedUserMessage(sessionId, "今天作业堆得太多了，我很焦虑也很累，撑不住了。");

        // Simulate the real DialogFinished fan-out: EmotionTraceListener AND
        // MemoryExtractListener (via memoryService.extractFromSession) both fire
        // for the SAME session. Before FIX 1, extractFromSession raw-inserted a
        // second, un-enriched (null-source) trace -> two rows. After FIX 1 both
        // converge on writeTrace upsert -> exactly one enriched row.
        emotionTraceListener.onDialogFinished(new DialogFinishedEvent(userId, sessionId));
        awaitTrace(userId, sessionId);
        memoryService.extractFromSession(userId, sessionId);

        assertEquals(1, traceCount(userId, sessionId),
                "both listeners on the same session must yield exactly one trace (de-dup)");

        Map<String, Object> trace = jdbc.queryForMap(
                "SELECT analysis_source, emotion_spectrum FROM tb_emotion_trace "
                        + "WHERE user_id = ? AND source_session_id = ?", userId, sessionId);
        assertNotNull(trace.get("analysis_source"),
                "the surviving trace must be enriched (non-null analysis_source), not the old raw row");
        assertNotNull(trace.get("emotion_spectrum"),
                "the surviving trace must carry a spectrum");
    }

    @Test
    @DisplayName("Diary settle inserts a null-session trace")
    void diarySettle_insertsNullSessionTrace() {
        Long userId = seedUser();

        Integer before = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_emotion_trace WHERE user_id = ? AND source_session_id IS NULL",
                Integer.class, userId);

        memorySettlementService.settleDiary(userId, "今天写下心声日记，心里有点难过，但也有一点平静。");

        Integer after = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_emotion_trace WHERE user_id = ? AND source_session_id IS NULL",
                Integer.class, userId);
        assertEquals(before + 1, after, "diary settle must insert exactly one null-session trace");

        Map<String, Object> trace = jdbc.queryForMap(
                "SELECT analysis_source, emotion_spectrum FROM tb_emotion_trace "
                        + "WHERE user_id = ? AND source_session_id IS NULL ORDER BY id DESC LIMIT 1", userId);
        assertEquals("SETTLEMENT", trace.get("analysis_source"));
        assertNotNull(trace.get("emotion_spectrum"));
    }
}
