package com.innercosmos.service;

import com.innercosmos.service.impl.GravityServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GravityServiceTest {

    private GravityServiceImpl gravityService;

    @BeforeEach
    void setUp() {
        gravityService = new GravityServiceImpl();
    }

    @Test
    @DisplayName("All positive values produce positive result")
    void allPositiveValues_producesPositiveResult() {
        double result = gravityService.calculateGravity(5.0, 3, 0.8, 10, 0);
        assertTrue(result > 0, "Gravity should be positive with all positive inputs");
    }

    @Test
    @DisplayName("Zero daysSinceLastTouched means no time decay")
    void zeroDaysSinceLastTouched_noTimeDecay() {
        double result = gravityService.calculateGravity(5.0, 3, 0.8, 10, 0);
        double expectedBase = 0.40 * 5.0 + 0.25 * 3 + 0.25 * 0.8 + 0.10 * 10;
        double expected = Math.log(1 + Math.max(expectedBase, 0)) * Math.exp(0);
        assertEquals(expected, result, 0.0001,
                "With zero days, exp decay factor should be 1.0 (no decay)");
    }

    @Test
    @DisplayName("Large daysSinceLastTouched causes significant decay")
    void largeDaysSinceLastTouched_significantDecay() {
        double recentResult = gravityService.calculateGravity(5.0, 3, 0.8, 10, 1);
        double oldResult = gravityService.calculateGravity(5.0, 3, 0.8, 10, 365);
        assertTrue(recentResult > oldResult,
                "Recently touched item should have higher gravity than old item");
        assertTrue(oldResult < recentResult * 0.1,
                "365-day-old item should have significantly less gravity");
    }

    @Test
    @DisplayName("Zero intensity with other positive values still yields positive result")
    void zeroIntensity_withOtherValues_yieldsPositiveResult() {
        double result = gravityService.calculateGravity(0.0, 3, 0.8, 10, 0);
        double expectedBase = 0.40 * 0.0 + 0.25 * 3 + 0.25 * 0.8 + 0.10 * 10;
        double expected = Math.log(1 + Math.max(expectedBase, 0)) * Math.exp(0);
        assertEquals(expected, result, 0.0001,
                "Zero intensity does not zero out result since other factors contribute");
        assertTrue(result > 0, "Result should still be positive from other factors");
    }

    @Test
    @DisplayName("All zero inputs produce zero result")
    void allZeroInputs_producesZeroResult() {
        double result = gravityService.calculateGravity(0.0, 0, 0.0, 0, 0);
        assertEquals(0.0, result, 0.0001,
                "All zeros mean base=0, log(1)=0");
    }

    @Test
    @DisplayName("High recurrence increases gravity")
    void highRecurrence_increasesGravity() {
        double lowRecurrence = gravityService.calculateGravity(5.0, 1, 0.8, 10, 0);
        double highRecurrence = gravityService.calculateGravity(5.0, 100, 0.8, 10, 0);
        assertTrue(highRecurrence > lowRecurrence,
                "Higher recurrence count should increase gravity");
    }

    @Test
    @DisplayName("High trigger count increases gravity")
    void highTriggerCount_increasesGravity() {
        double lowTrigger = gravityService.calculateGravity(5.0, 3, 0.8, 1, 0);
        double highTrigger = gravityService.calculateGravity(5.0, 3, 0.8, 100, 0);
        assertTrue(highTrigger > lowTrigger,
                "Higher trigger count should increase gravity");
    }

    @Test
    @DisplayName("Higher user importance increases gravity")
    void higherImportance_increasesGravity() {
        double lowImportance = gravityService.calculateGravity(5.0, 3, 0.2, 10, 0);
        double highImportance = gravityService.calculateGravity(5.0, 3, 1.0, 10, 0);
        assertTrue(highImportance > lowImportance,
                "Higher user importance should increase gravity");
    }

    @Test
    @DisplayName("Negative daysSinceLastTouched is clamped to zero (no negative decay)")
    void negativeDays_clampedToZero() {
        double resultNeg = gravityService.calculateGravity(5.0, 3, 0.8, 10, -5);
        double resultZero = gravityService.calculateGravity(5.0, 3, 0.8, 10, 0);
        assertEquals(resultZero, resultNeg, 0.0001,
                "Negative days should be clamped to 0, matching zero-day result");
    }

    @Test
    @DisplayName("Very large values do not overflow or produce NaN")
    void veryLargeValues_noOverflow() {
        double result = gravityService.calculateGravity(
                Double.MAX_VALUE, Integer.MAX_VALUE, Double.MAX_VALUE,
                Integer.MAX_VALUE, Long.MAX_VALUE);
        assertFalse(Double.isNaN(result), "Result should not be NaN");
        assertFalse(Double.isInfinite(result), "Result should not be infinite");
    }
}
