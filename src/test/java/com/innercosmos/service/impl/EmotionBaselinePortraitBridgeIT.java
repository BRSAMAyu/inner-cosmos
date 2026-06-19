package com.innercosmos.service.impl;

import com.innercosmos.ai.semantic.EmotionBaseline;
import com.innercosmos.ai.semantic.EmotionInsight;
import com.innercosmos.service.EmotionBaselineService;
import com.innercosmos.service.EmotionInsightService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IC-EMO-003 anti-thrash invariant (Spec §2), proven against real H2:
 *
 * <p>(a) Writing ONE extreme EmotionTrace and doing nothing else leaves the emotion
 * portrait dims UNCHANGED — the per-trace path does not touch the portrait.
 *
 * <p>(b) Only after several days of traces are seeded and the explicit
 * baseline→portrait bridge runs does EMOTION_PATTERN appear/change to reflect the
 * computed baseline. So a momentary mood swing never moves the portrait; the
 * multi-day baseline recompute does.
 */
@SpringBootTest(properties = {
        "llm.mode=dev",
        "llm.provider=mock",
        "llm.allow-fallback=true"
})
class EmotionBaselinePortraitBridgeIT {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EmotionBaselineService baselineService;
    @Autowired
    private EmotionInsightService insightService;

    private Long seedUser() {
        String username = "emo-baseline-it-" + System.nanoTime();
        jdbc.update("INSERT INTO tb_user (username, password_hash, role, status) VALUES (?, ?, ?, ?)",
                username, "hash", "USER", "ACTIVE");
        return jdbc.queryForObject("SELECT id FROM tb_user WHERE username = ?", Long.class, username);
    }

    private void seedTrace(Long userId, Long sessionId, String emotion, double score, LocalDate date) {
        jdbc.update("INSERT INTO tb_emotion_trace "
                        + "(user_id, source_session_id, emotion_name, emotion_score, weather_type, "
                        + "trigger_scene, record_date, emotion_spectrum, analysis_source) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                userId, sessionId, emotion, score, "RAINY", "压力", date,
                "[{\"emotion\":\"" + emotion + "\",\"ratio\":1.0}]", "LEXICON");
    }

    private int portraitCount(Long userId, String dim) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_user_portrait WHERE user_id = ? AND dim = ?",
                Integer.class, userId, dim);
    }

    @Test
    @DisplayName("anti-thrash: a single extreme trace alone does NOT move the emotion portrait")
    void singleTrace_doesNotMovePortrait() {
        Long userId = seedUser();

        // One extreme momentary trace.
        seedTrace(userId, 9001L, "暴怒", 10.0, LocalDate.now());

        // No bridge invoked -> portrait must be untouched.
        assertEquals(0, portraitCount(userId, EmotionBaselineServiceImpl.DIM_EMOTION_PATTERN),
                "a single per-trace write must NOT create/modify the emotion portrait dim");
        assertEquals(0, portraitCount(userId, EmotionBaselineServiceImpl.DIM_CURRENT_STATE),
                "a single per-trace write must NOT create/modify the current-state dim");
    }

    @Test
    @DisplayName("anti-thrash: the REAL per-trace write path (EmotionInsightService.writeTrace) does NOT move the emotion portrait")
    void realWriteTracePath_doesNotMovePortrait() {
        Long userId = seedUser();

        // Drive the ACTUAL production per-trace path the way a real dialog-finish does:
        // analyze() free text into an insight (mock mode -> deterministic LEXICON), then
        // writeTrace() persists it as an enriched EmotionTrace. This is NOT a hand-built
        // emotionTraceMapper.insert — it locks the invariant against future regressions
        // where someone might wire a portrait write into the per-trace path.
        EmotionInsight insight = insightService.analyze(userId, "气死了，我现在非常暴怒！");
        // Make the trace unambiguously extreme so any accidental portrait write would be
        // visible and could not be argued away as "below a threshold".
        insight.primaryEmotion = "暴怒";
        insight.emotionScore = 10.0;
        insightService.writeTrace(userId, 9101L, insight);

        // The trace really landed via the production path...
        assertEquals(1, jdbc.queryForObject(
                        "SELECT COUNT(*) FROM tb_emotion_trace WHERE user_id = ? AND source_session_id = ?",
                        Integer.class, userId, 9101L),
                "writeTrace must persist exactly one enriched trace via the production upsert path");

        // ...yet WITHOUT bridgeToPortrait, all three emotion portrait dims stay absent.
        assertEquals(0, portraitCount(userId, EmotionBaselineServiceImpl.DIM_EMOTION_PATTERN),
                "the real per-trace write path must NOT create/modify the EMOTION_PATTERN dim");
        assertEquals(0, portraitCount(userId, EmotionBaselineServiceImpl.DIM_CURRENT_STATE),
                "the real per-trace write path must NOT create/modify the CURRENT_STATE dim");
        assertEquals(0, portraitCount(userId, EmotionBaselineServiceImpl.DIM_ENERGY_RHYTHM),
                "the real per-trace write path must NOT create/modify the ENERGY_RHYTHM dim");
    }

    @Test
    @DisplayName("baseline bridge: several days of traces -> emotion portrait reflects the baseline")
    void baselineBridge_movesPortrait() {
        Long userId = seedUser();

        // Several days of mostly-平静 traces (recent ones weigh more).
        seedTrace(userId, 1L, "平静", 5.0, LocalDate.now().minusDays(5));
        seedTrace(userId, 2L, "平静", 5.5, LocalDate.now().minusDays(4));
        seedTrace(userId, 3L, "平静", 4.5, LocalDate.now().minusDays(3));
        seedTrace(userId, 4L, "平静", 5.0, LocalDate.now().minusDays(2));
        seedTrace(userId, 5L, "平静", 5.5, LocalDate.now().minusDays(1));

        // Before bridge: portrait untouched.
        assertEquals(0, portraitCount(userId, EmotionBaselineServiceImpl.DIM_EMOTION_PATTERN));

        // Run the baseline -> portrait bridge.
        EmotionBaseline baseline = baselineService.bridgeToPortrait(userId);
        assertTrue(baseline.present, "baseline should be present after several traces");
        assertEquals("平静", baseline.dominantEmotion);

        // After bridge: the emotion portrait dim now exists and reflects the baseline.
        assertEquals(1, portraitCount(userId, EmotionBaselineServiceImpl.DIM_EMOTION_PATTERN),
                "baseline bridge must create the EMOTION_PATTERN portrait dim");
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT value_json, confidence FROM tb_user_portrait WHERE user_id = ? AND dim = ?",
                userId, EmotionBaselineServiceImpl.DIM_EMOTION_PATTERN);
        assertTrue(String.valueOf(row.get("value_json")).contains("平静"),
                "portrait value must reflect the baseline dominant emotion");
        double confidence = ((Number) row.get("confidence")).doubleValue();
        assertTrue(confidence > 0.0 && confidence <= 0.85,
                "confidence scales with depth/stability and stays calm (<=0.85)");

        // CURRENT_STATE + ENERGY_RHYTHM also bridged.
        assertEquals(1, portraitCount(userId, EmotionBaselineServiceImpl.DIM_CURRENT_STATE));
        assertEquals(1, portraitCount(userId, EmotionBaselineServiceImpl.DIM_ENERGY_RHYTHM));
    }

    @Test
    @DisplayName("anti-thrash contrast: extreme single trace vs steady baseline -> only baseline moves portrait")
    void extremeSingle_vsBaseline() {
        Long userId = seedUser();

        // (a) one extreme trace, no bridge -> nothing in portrait.
        seedTrace(userId, 7001L, "暴怒", 10.0, LocalDate.now());
        assertEquals(0, portraitCount(userId, EmotionBaselineServiceImpl.DIM_EMOTION_PATTERN));

        // (b) add steady 平静 days so the multi-day baseline is dominated by 平静,
        // then bridge: the portrait reflects the BASELINE, not the lone extreme.
        seedTrace(userId, 7002L, "平静", 5.0, LocalDate.now().minusDays(4));
        seedTrace(userId, 7003L, "平静", 5.0, LocalDate.now().minusDays(3));
        seedTrace(userId, 7004L, "平静", 5.0, LocalDate.now().minusDays(2));
        seedTrace(userId, 7005L, "平静", 5.0, LocalDate.now().minusDays(1));

        EmotionBaseline baseline = baselineService.bridgeToPortrait(userId);
        assertEquals("平静", baseline.dominantEmotion,
                "the steady multi-day baseline (not the single 暴怒 spike) dominates");
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT value_json FROM tb_user_portrait WHERE user_id = ? AND dim = ?",
                userId, EmotionBaselineServiceImpl.DIM_EMOTION_PATTERN);
        assertTrue(String.valueOf(row.get("value_json")).contains("平静"));
        assertFalse(String.valueOf(row.get("value_json")).contains("暴怒"),
                "the lone extreme trace must not drive the portrait");
    }
}
