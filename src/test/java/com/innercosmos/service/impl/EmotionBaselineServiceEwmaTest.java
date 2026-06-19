package com.innercosmos.service.impl;

import com.innercosmos.ai.semantic.EmotionBaseline;
import com.innercosmos.entity.EmotionTrace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IC-EMO-003 unit coverage for the deterministic EWMA baseline math in
 * {@link EmotionBaselineServiceImpl#computeFromTraces}. No Spring, no DB: pure
 * function. Asserts EXACT computed values (alpha = {@link EmotionBaselineServiceImpl#EWMA_ALPHA})
 * and that two identical calls are byte-for-byte identical (determinism).
 */
class EmotionBaselineServiceEwmaTest {

    /** Build the service with null collaborators — computeFromTraces touches no DB. */
    private final EmotionBaselineServiceImpl svc =
            new EmotionBaselineServiceImpl(null, null);

    private EmotionTrace trace(LocalDate date, String emotion, double score) {
        EmotionTrace t = new EmotionTrace();
        t.recordDate = date;
        t.emotionName = emotion;
        t.emotionScore = score;
        return t;
    }

    @Test
    @DisplayName("empty history -> well-formed absent baseline, never null")
    void empty() {
        EmotionBaseline b = svc.computeFromTraces(List.of(), 14);
        assertNotNull(b);
        assertFalse(b.present);
        assertEquals(0, b.sampleCount);
        assertEquals(14, b.windowDays);
        assertEquals(1.0, b.stabilityScore, 1e-9, "absent => perfectly stable by convention");
        assertNull(b.dominantEmotion);

        // null input also degrades gracefully.
        EmotionBaseline n = svc.computeFromTraces(null, 7);
        assertNotNull(n);
        assertFalse(n.present);
    }

    @Test
    @DisplayName("single trace -> mean = its score, zero variance, stability 1.0, dominant = its emotion")
    void single() {
        EmotionBaseline b = svc.computeFromTraces(
                List.of(trace(LocalDate.of(2026, 6, 10), "平静", 7.0)), 14);
        assertTrue(b.present);
        assertEquals(1, b.sampleCount);
        assertEquals(7.0, b.intensityMean, 1e-9);
        assertEquals(0.0, b.intensityVariance, 1e-9);
        assertEquals(1.0, b.stabilityScore, 1e-9);
        assertEquals("平静", b.dominantEmotion);
        assertNotNull(b.baselineLabel);
        assertFalse(b.baselineLabel.isBlank());
    }

    @Test
    @DisplayName("multi-day EWMA (alpha=0.3) on [6,8,4] -> exact mean/variance/stability")
    void multiDayExactMath() {
        List<EmotionTrace> traces = List.of(
                trace(LocalDate.of(2026, 6, 1), "喜悦", 6.0),
                trace(LocalDate.of(2026, 6, 2), "喜悦", 8.0),
                trace(LocalDate.of(2026, 6, 3), "焦虑", 4.0));

        EmotionBaseline b = svc.computeFromTraces(traces, 14);
        assertTrue(b.present);
        assertEquals(3, b.sampleCount);
        // Hand-computed EWMA with alpha=0.3, init=first value:
        //   ewma: 6 -> 6.6 -> 5.82 ; ewmvar -> 2.0076
        assertEquals(5.82, b.intensityMean, 1e-9);
        assertEquals(2.0076, b.intensityVariance, 1e-9);
        assertEquals(1.0 / (1.0 + 2.0076), b.stabilityScore, 1e-9);
    }

    @Test
    @DisplayName("input order does not matter: traces are sorted by recordDate asc internally")
    void orderIndependent() {
        List<EmotionTrace> ascending = List.of(
                trace(LocalDate.of(2026, 6, 1), "喜悦", 6.0),
                trace(LocalDate.of(2026, 6, 2), "喜悦", 8.0),
                trace(LocalDate.of(2026, 6, 3), "焦虑", 4.0));
        List<EmotionTrace> shuffled = new ArrayList<>(ascending);
        Collections.reverse(shuffled);

        EmotionBaseline a = svc.computeFromTraces(ascending, 14);
        EmotionBaseline z = svc.computeFromTraces(shuffled, 14);
        assertEquals(a.intensityMean, z.intensityMean, 1e-12);
        assertEquals(a.intensityVariance, z.intensityVariance, 1e-12);
    }

    @Test
    @DisplayName("determinism: same input -> byte-identical numeric output across two calls")
    void deterministic() {
        List<EmotionTrace> traces = Arrays.asList(
                trace(LocalDate.of(2026, 6, 1), "平静", 5.0),
                trace(LocalDate.of(2026, 6, 2), "焦虑", 7.5),
                trace(LocalDate.of(2026, 6, 3), "平静", 5.5),
                trace(LocalDate.of(2026, 6, 4), "喜悦", 6.0));
        EmotionBaseline b1 = svc.computeFromTraces(traces, 14);
        EmotionBaseline b2 = svc.computeFromTraces(traces, 14);
        assertEquals(b1.intensityMean, b2.intensityMean, 0.0);
        assertEquals(b1.intensityVariance, b2.intensityVariance, 0.0);
        assertEquals(b1.stabilityScore, b2.stabilityScore, 0.0);
        assertEquals(b1.dominantEmotion, b2.dominantEmotion);
        assertEquals(b1.baselineLabel, b2.baselineLabel);
    }

    @Test
    @DisplayName("all-same emotion -> that emotion dominates; mixed -> weighted-recency argmax")
    void dominantEmotion() {
        EmotionBaseline same = svc.computeFromTraces(List.of(
                trace(LocalDate.of(2026, 6, 1), "平静", 5.0),
                trace(LocalDate.of(2026, 6, 2), "平静", 6.0),
                trace(LocalDate.of(2026, 6, 3), "平静", 5.0)), 14);
        assertEquals("平静", same.dominantEmotion);

        // recent traces weigh more: two recent 焦虑 should outweigh one old 喜悦.
        EmotionBaseline mixed = svc.computeFromTraces(List.of(
                trace(LocalDate.of(2026, 6, 1), "喜悦", 5.0),
                trace(LocalDate.of(2026, 6, 2), "焦虑", 5.0),
                trace(LocalDate.of(2026, 6, 3), "焦虑", 5.0)), 14);
        assertEquals("焦虑", mixed.dominantEmotion);
    }
}
