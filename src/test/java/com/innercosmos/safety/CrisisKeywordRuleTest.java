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

    // ── Gemini audit 3.7 (CONFIRMED/P0): zero-width / full-width / inserted-whitespace
    //    bypass adversarial eval, CN + EN variants ──────────────────────────────────────

    @Test
    @DisplayName("3.7: a zero-width space inserted mid-keyword no longer bypasses detection (CN)")
    void zeroWidthSpaceMidKeyword_stillBlocks() {
        assertTrue(rule.match("我想自​杀").matched, "U+200B zero-width space mid-keyword must not defeat matching");
        assertTrue(rule.match("我‌想‍跳楼").matched, "ZWNJ/ZWJ scattered through the keyword must not defeat matching");
        assertTrue(rule.match("﻿我想自杀").matched, "a leading BOM/zero-width-no-break-space must not defeat matching");
    }

    @Test
    @DisplayName("3.7: a zero-width space mid-phrase no longer bypasses detection (EN)")
    void zeroWidthSpace_englishPhrase_stillBlocks() {
        assertTrue(rule.match("I want to kill​ myself").matched);
        assertTrue(rule.match("i want to​ die").matched);
    }

    @Test
    @DisplayName("3.7: an ordinary space inserted mid-keyword no longer bypasses detection (CN)")
    void insertedWhitespaceMidKeyword_stillBlocks() {
        assertTrue(rule.match("我想自 杀").matched, "a plain space inserted between CJK characters of a keyword must not defeat matching");
        assertTrue(rule.match("跳 楼").matched);
    }

    @Test
    @DisplayName("3.7: full-width (compatibility) character variants no longer bypass detection")
    void fullWidthVariant_stillBlocks() {
        // Full-width Latin letters -- NFKC normalizes these to standard half-width ASCII.
        assertTrue(rule.match("ｋｉｌｌ ｍｙｓｅｌｆ").matched, "full-width Latin must NFKC-normalize to the plain ASCII keyword");
    }

    @Test
    @DisplayName("3.7: ordinary venting with incidental zero-width characters (e.g. from copy-paste) is still NOT a false positive")
    void zeroWidthCharacters_doNotCauseFalsePositiveOnOrdinaryText() {
        assertFalse(rule.match("今天​加班累死了").matched,
                "stripping zero-width noise must not turn ordinary venting into a false positive");
    }
}
