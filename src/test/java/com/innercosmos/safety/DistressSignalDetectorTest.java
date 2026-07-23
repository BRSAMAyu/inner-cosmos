package com.innercosmos.safety;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gemini audit 3.7 (CONFIRMED/P0): the distress-signal tier (the second detection channel,
 * separate from CrisisKeywordRule) must share the same Unicode normalization -- otherwise a
 * zero-width-obfuscated implicit-crisis phrase would slip past this channel even after the
 * explicit-keyword channel was hardened, defeating the point of a SHARED chokepoint.
 */
class DistressSignalDetectorTest {

    private final DistressSignalDetector detector = new DistressSignalDetector();

    @Test
    @DisplayName("ordinary distress signal is detected")
    void ordinaryDistressSignal_isDetected() {
        assertTrue(detector.hasDistressSignal("我觉得自己是家里的累赘"));
        assertTrue(detector.hasDistressSignal("最近总想消失"));
    }

    @Test
    @DisplayName("null/blank text is never a distress signal")
    void nullOrBlank_isNotADistressSignal() {
        assertFalse(detector.hasDistressSignal(null));
        assertFalse(detector.hasDistressSignal(""));
        assertFalse(detector.hasDistressSignal("   "));
    }

    @Test
    @DisplayName("3.7: a zero-width space inserted mid-signal no longer bypasses this channel")
    void zeroWidthSpaceMidSignal_stillDetected() {
        assertTrue(detector.hasDistressSignal("我是累​赘"), "zero-width space must not defeat the distress-signal channel either");
    }

    @Test
    @DisplayName("3.7: an ordinary space inserted mid-signal no longer bypasses this channel")
    void insertedWhitespaceMidSignal_stillDetected() {
        assertTrue(detector.hasDistressSignal("想 消失"));
    }

    @Test
    @DisplayName("ordinary conversation without any distress vocabulary is not flagged")
    void ordinaryConversation_isNotFlagged() {
        assertFalse(detector.hasDistressSignal("今天天气不错，出去散步了"));
    }
}
