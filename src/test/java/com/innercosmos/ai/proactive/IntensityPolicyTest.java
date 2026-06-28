package com.innercosmos.ai.proactive;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IntensityPolicyTest {

    private final IntensityPolicy policy = new IntensityPolicy();

    @Test
    void offHasZeroPerDay() {
        assertEquals(0, policy.get("OFF").maxPerDay());
    }

    @Test
    void whisperHasZeroPerDay() {
        assertEquals(0, policy.get("WHISPER").maxPerDay());
    }

    @Test
    void lightHasThreePerDay() {
        assertEquals(3, policy.get("LIGHT").maxPerDay());
    }

    @Test
    void activeHasEightPerDay() {
        assertEquals(8, policy.get("ACTIVE").maxPerDay());
    }

    @Test
    void companionHasTwelvePerDay() {
        assertEquals(12, policy.get("COMPANION").maxPerDay());
    }

    @Test
    void aliveHasUnboundedPerDay() {
        assertEquals(Integer.MAX_VALUE, policy.get("ALIVE").maxPerDay());
    }

    @Test
    void unknownDefaultsToLight() {
        // Unknown intensities fall back to the LIGHT policy.
        assertEquals(policy.get("LIGHT").maxPerDay(), policy.get("UNKNOWN").maxPerDay());
    }

    @Test
    void isAliveReturnsTrueForAlive() {
        assertTrue(policy.isAlive("ALIVE"));
        assertTrue(policy.isAlive("alive"));
    }

    @Test
    void isAliveReturnsFalseForOthers() {
        assertFalse(policy.isAlive("LIGHT"));
        assertFalse(policy.isAlive("COMPANION"));
        assertFalse(policy.isAlive("off"));
    }
}