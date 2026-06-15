package com.innercosmos.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * VS-006: ensures EmotionTrace.emotionScore and BeliefPattern.strengthScore
 * cannot hold an out-of-range value — inputs beyond the valid range are clamped
 * to the nearest bound at write time.
 */
class ScoreRangeValidationTest {

    // --- EmotionTrace (codebase 0..10 scale) ---

    @Test
    @DisplayName("EmotionTrace score below 0 is clamped to 0")
    void emotionScore_belowMin_clampedToZero() {
        assertEquals(0.0, EmotionTrace.clampScore(-3.7));
    }

    @Test
    @DisplayName("EmotionTrace score above 10 is clamped to 10")
    void emotionScore_aboveMax_clampedToTen() {
        assertEquals(10.0, EmotionTrace.clampScore(99.0));
    }

    @Test
    @DisplayName("EmotionTrace in-range score passes through unchanged")
    void emotionScore_inRange_unchanged() {
        assertEquals(4.5, EmotionTrace.clampScore(4.5));
        assertEquals(0.0, EmotionTrace.clampScore(0.0));
        assertEquals(10.0, EmotionTrace.clampScore(10.0));
    }

    @Test
    @DisplayName("EmotionTrace null score returns null (no score available)")
    void emotionScore_null_returnsNull() {
        assertNull(EmotionTrace.clampScore(null));
    }

    // --- BeliefPattern (normalized 0..1 scale) ---

    @Test
    @DisplayName("BeliefPattern strength below 0 is clamped to 0")
    void strengthScore_belowMin_clampedToZero() {
        assertEquals(0.0, BeliefPattern.clampStrength(-0.4));
    }

    @Test
    @DisplayName("BeliefPattern strength above 1 is clamped to 1")
    void strengthScore_aboveMax_clampedToOne() {
        assertEquals(1.0, BeliefPattern.clampStrength(1.7));
    }

    @Test
    @DisplayName("BeliefPattern in-range strength passes through unchanged")
    void strengthScore_inRange_unchanged() {
        assertEquals(0.3, BeliefPattern.clampStrength(0.3));
        assertEquals(0.0, BeliefPattern.clampStrength(0.0));
        assertEquals(1.0, BeliefPattern.clampStrength(1.0));
    }

    @Test
    @DisplayName("BeliefPattern null strength returns null (no score available)")
    void strengthScore_null_returnsNull() {
        assertNull(BeliefPattern.clampStrength(null));
    }
}
