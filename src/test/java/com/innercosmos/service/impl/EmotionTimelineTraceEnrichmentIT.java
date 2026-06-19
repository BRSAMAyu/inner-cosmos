package com.innercosmos.service.impl;

import com.innercosmos.entity.EmotionTimeline;
import com.innercosmos.service.EmotionTimelineService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IC-EMO-003 item 5: the timeline can ALSO be built from enriched EmotionTrace
 * rows (deterministically, no LLM) and surfaces baseline/stability for the view.
 * Additive — existing MemoryCard-based aggregateForDate is untouched.
 */
@SpringBootTest(properties = {
        "llm.mode=dev",
        "llm.provider=mock",
        "llm.allow-fallback=true"
})
class EmotionTimelineTraceEnrichmentIT {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EmotionTimelineService timelineService;

    private Long seedUser() {
        String username = "emo-tl-it-" + System.nanoTime();
        jdbc.update("INSERT INTO tb_user (username, password_hash, role, status) VALUES (?, ?, ?, ?)",
                username, "hash", "USER", "ACTIVE");
        return jdbc.queryForObject("SELECT id FROM tb_user WHERE username = ?", Long.class, username);
    }

    private void seedTrace(Long userId, Long sessionId, String emotion, double score, LocalDate date) {
        jdbc.update("INSERT INTO tb_emotion_trace "
                        + "(user_id, source_session_id, emotion_name, emotion_score, weather_type, "
                        + "trigger_scene, record_date, emotion_spectrum, analysis_source) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                userId, sessionId, emotion, score, "SUNNY", "学习", date,
                "[{\"emotion\":\"" + emotion + "\",\"ratio\":0.7},{\"emotion\":\"期待\",\"ratio\":0.3}]",
                "LEXICON");
    }

    @Test
    @DisplayName("aggregateFromTraces builds a deterministic timeline row from EmotionTrace data")
    void aggregateFromTraces_buildsRow() {
        Long userId = seedUser();
        LocalDate day = LocalDate.now();
        seedTrace(userId, 11L, "喜悦", 7.0, day);
        seedTrace(userId, 12L, "喜悦", 6.0, day);
        seedTrace(userId, 13L, "平静", 5.0, day);

        timelineService.aggregateFromTraces(userId, day);

        EmotionTimeline row = timelineService.getTimeline(userId, day, day).get(0);
        assertEquals("喜悦", row.dominantEmotion, "most frequent trace emotion dominates");
        assertNotNull(row.emotionSpectrum);
        assertNotNull(row.intensityAverage);
        // mean of 7,6,5 = 6.0
        assertEquals(6.0, row.intensityAverage, 1e-9);
        assertEquals(3, row.memoryCount);
    }

    @Test
    @DisplayName("aggregateFromTraces is idempotent (re-running updates, not duplicates)")
    void aggregateFromTraces_idempotent() {
        Long userId = seedUser();
        LocalDate day = LocalDate.now();
        seedTrace(userId, 21L, "平静", 5.0, day);

        timelineService.aggregateFromTraces(userId, day);
        timelineService.aggregateFromTraces(userId, day);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_emotion_timeline WHERE user_id = ? AND record_date = ?",
                Integer.class, userId, day);
        assertEquals(1, count, "re-running must upsert, not duplicate");
    }

    @Test
    @DisplayName("getTimelineView surfaces trend + baseline + stability for visualization")
    void timelineView_surfacesBaseline() {
        Long userId = seedUser();
        seedTrace(userId, 31L, "平静", 5.0, LocalDate.now().minusDays(2));
        seedTrace(userId, 32L, "平静", 5.5, LocalDate.now().minusDays(1));
        seedTrace(userId, 33L, "平静", 5.0, LocalDate.now());

        EmotionTimelineService.EmotionTimelineView view =
                timelineService.getTimelineView(userId, 14);

        assertNotNull(view);
        assertNotNull(view.baseline);
        assertTrue(view.baseline.present, "baseline computed from traces");
        assertEquals("平静", view.baseline.dominantEmotion);
        assertTrue(view.stabilityScore >= 0.0 && view.stabilityScore <= 1.0);
        assertNotNull(view.trend, "trend points present for the chart");
    }
}
