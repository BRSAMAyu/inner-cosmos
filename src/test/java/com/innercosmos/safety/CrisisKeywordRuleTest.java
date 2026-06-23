package com.innercosmos.safety;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins M-020: crisis detection must not miss lethal means, English phrases, or homophone
 * bypasses — and must still leave ordinary venting alone.
 */
class CrisisKeywordRuleTest {

    private final CrisisKeywordRule rule = new CrisisKeywordRule();

    @Test
    @DisplayName("M-020: lethal means / scene / homophone terms block (HIGH)")
    void lethalMeansAndHomophones_block() {
        assertTrue(rule.match("我想烧炭").matched);
        assertTrue(rule.match("我准备上吊").matched);
        assertTrue(rule.match("想紫砂了").matched, "紫砂 is a homophone bypass for 自杀");
        assertTrue(rule.match("我留了遗书").matched);
        assertEquals("HIGH", rule.match("我想烧炭").riskLevel);
    }

    @Test
    @DisplayName("M-020: English crisis phrases block case-insensitively")
    void englishCrisis_blocksCaseInsensitively() {
        assertTrue(rule.match("I want to kill myself").matched);
        assertTrue(rule.match("i want to die tonight").matched);
        assertTrue(rule.match("feeling SUICIDAL today").matched);
        assertEquals("HIGH", rule.match("I want to kill myself").riskLevel);
    }

    @Test
    @DisplayName("existing direct crisis terms still block")
    void existingCrisisTerms_block() {
        assertTrue(rule.match("我想自杀").matched);
        assertTrue(rule.match("不想活了").matched); // matches 不想活
    }

    @Test
    @DisplayName("ordinary venting does NOT block (false-positive guard)")
    void ordinaryVenting_doesNotBlock() {
        assertFalse(rule.match("今天加班累死了").matched);
        assertFalse(rule.match("活着好累啊").matched);
        assertFalse(rule.match("考试压力好大").matched);
    }
}
