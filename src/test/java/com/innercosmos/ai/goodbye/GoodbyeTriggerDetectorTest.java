package com.innercosmos.ai.goodbye;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GoodbyeTriggerDetectorTest {

    private final GoodbyeTriggerDetector detector = new GoodbyeTriggerDetector();

    @Test
    void detectsHighConfidence_goodbye() {
        var d = detector.detect("今天到这吧");
        assertEquals("LANGUAGE_HIGH", d.trigger());
        assertEquals(0.95, d.confidence());
        assertFalse(d.needsConfirm());
    }

    @Test
    void detectsHighConfidence_sleep() {
        var d = detector.detect("我先睡了");
        assertEquals("LANGUAGE_HIGH", d.trigger());
        assertFalse(d.needsConfirm());
    }

    @Test
    void detectsHighConfidence_guan() {
        var d = detector.detect("拜拜，明天见");
        assertEquals("LANGUAGE_HIGH", d.trigger());
        assertFalse(d.needsConfirm());
    }

    @Test
    void detectsMediumAndRequiresConfirm() {
        var d = detector.detect("有点累了，先放着吧");
        assertEquals("LANGUAGE_MEDIUM", d.trigger());
        assertEquals(0.65, d.confidence());
        assertTrue(d.needsConfirm());
    }

    @Test
    void detectsMedium_later() {
        var d = detector.detect("之后再聊吧");
        assertEquals("LANGUAGE_MEDIUM", d.trigger());
        assertTrue(d.needsConfirm());
    }

    @Test
    void ignoresUnrelatedText() {
        var d = detector.detect("今天项目 deadline 很赶");
        assertNull(d.trigger());
        assertEquals(0.0, d.confidence());
        assertFalse(d.needsConfirm());
    }

    @Test
    void ignoresEmptyMessage() {
        var d = detector.detect("");
        assertNull(d.trigger());
    }

    @Test
    void ignoresNullMessage() {
        var d = detector.detect(null);
        assertNull(d.trigger());
    }

    @Test
    void partialMatchDoesNotTrigger() {
        var d = detector.detect("今天到了这里吗？");
        assertNull(d.trigger());
    }
}