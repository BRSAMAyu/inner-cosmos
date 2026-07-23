package com.innercosmos.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gemini audit 3.7 (CONFIRMED/P0): direct unit coverage for the shared normalization chokepoint
 * every crisis/abuse/distress matcher runs through.
 */
class SafetyTextNormalizerTest {

    @Test
    @DisplayName("null is normalized to an empty string, never throws")
    void nullInput_isEmptyString() {
        assertEquals("", SafetyTextNormalizer.normalizeForMatch(null));
    }

    @Test
    @DisplayName("zero-width space (U+200B) is stripped entirely")
    void zeroWidthSpace_isStripped() {
        assertEquals("自杀", SafetyTextNormalizer.normalizeForMatch("自​杀"));
    }

    @Test
    @DisplayName("zero-width non-joiner/joiner (U+200C/U+200D) are stripped entirely")
    void zeroWidthJoiners_areStripped() {
        assertEquals("跳楼", SafetyTextNormalizer.normalizeForMatch("跳‌‍楼"));
    }

    @Test
    @DisplayName("BOM / zero-width no-break space (U+FEFF) is stripped entirely")
    void byteOrderMark_isStripped() {
        assertEquals("自杀", SafetyTextNormalizer.normalizeForMatch("﻿自杀"));
    }

    @Test
    @DisplayName("word joiner (U+2060) is stripped entirely")
    void wordJoiner_isStripped() {
        assertEquals("自杀", SafetyTextNormalizer.normalizeForMatch("自⁠杀"));
    }

    @Test
    @DisplayName("full-width Latin letters NFKC-normalize to standard half-width ASCII")
    void fullWidthLatin_normalizesToHalfWidth() {
        assertEquals("kill myself", SafetyTextNormalizer.normalizeForMatch("ｋｉｌｌ ｍｙｓｅｌｆ"));
    }

    @Test
    @DisplayName("whitespace directly touching a CJK ideograph is stripped (mid-keyword insertion bypass)")
    void whitespaceTouchingCjk_isStripped() {
        assertEquals("自杀", SafetyTextNormalizer.normalizeForMatch("自 杀"));
        assertEquals("我想跳楼", SafetyTextNormalizer.normalizeForMatch("我 想 跳 楼"));
    }

    @Test
    @DisplayName("whitespace between purely ASCII words is preserved -- English multi-word phrases must still match")
    void whitespaceBetweenAsciiWords_isPreserved() {
        assertEquals("kill myself", SafetyTextNormalizer.normalizeForMatch("kill myself"));
        String sentence = "i will not let this thought define who i am";
        assertEquals(sentence, SafetyTextNormalizer.normalizeForMatch(sentence));
    }

    @Test
    @DisplayName("English text is lower-cased for case-insensitive matching")
    void englishText_isLowerCased() {
        assertEquals("i want to kill myself", SafetyTextNormalizer.normalizeForMatch("I Want To KILL Myself"));
    }

    @Test
    @DisplayName("a realistic combined obfuscation attempt (zero-width + full-width + inserted space) still normalizes to the plain keyword")
    void combinedObfuscation_stillNormalizesToPlainKeyword() {
        // Zero-width space between "自" and "杀", full-width word appended, extra space inserted.
        String obfuscated = "我​想 自​杀 ｎｏｗ";
        String normalized = SafetyTextNormalizer.normalizeForMatch(obfuscated);
        assertTrue(normalized.contains("自杀"), "combined zero-width + whitespace obfuscation must still collapse to the plain keyword");
        assertTrue(normalized.contains("now"), "full-width Latin must still normalize to ASCII even alongside other obfuscation");
    }
}
