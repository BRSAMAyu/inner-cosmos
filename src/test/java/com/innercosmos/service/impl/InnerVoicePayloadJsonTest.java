package com.innercosmos.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M2 (independent code review): the inner_voice SSE payload is built via
 * {@link AuroraAgentServiceImpl#buildInnerVoicePayload} using Jackson, not hand-rolled string
 * concatenation, so a raw control character (U+0000-U+001F) the LLM might emit in its composed
 * inner-voice line can never produce invalid JSON the frontend would silently drop. The old
 * escape() helper only covered backslash, quote, CR, LF, tab -- leaving backspace/form-feed/etc.
 * unescaped, which would yield a data line that fails JSON parsing.
 *
 * Java escape "\b" in a string literal is U+0008 (backspace) -- a control char the old escape()
 * did NOT cover. This test pins that case.
 */
class InnerVoicePayloadJsonTest {

    private final ObjectMapper parser = new ObjectMapper();

    @Test
    void buildInnerVoicePayload_producesValidJsonForNormalText() throws Exception {
        String payload = AuroraAgentServiceImpl.buildInnerVoicePayload(
                "其实我有点担心她今天的状态", "data:audio/mpeg;base64,AAA", "warm_gentle_female");
        JsonNode node = parser.readTree(payload);
        assertEquals("其实我有点担心她今天的状态", node.get("text").asText());
        assertEquals("data:audio/mpeg;base64,AAA", node.get("audio").asText());
        assertEquals("warm_gentle_female", node.get("voiceId").asText());
    }

    @Test
    void buildInnerVoicePayload_escapesQuotesAndBackslashes() throws Exception {
        String payload = AuroraAgentServiceImpl.buildInnerVoicePayload(
                "她说\"别走\"", "data:audio/mpeg;base64,Q", "v1");
        assertEquals("她说\"别走\"", parser.readTree(payload).get("text").asText());
    }

    // Failing-first against the pre-fix hand-rolled escape(): a raw backspace (U+0008, Java "\b")
    // inside the composed text must not break JSON validity. The old escape() left it raw, so the
    // emitted data line was invalid JSON that the frontend would silently drop; Jackson escapes it.
    @Test
    void buildInnerVoicePayload_keepsJsonValidForRawBackspace() throws Exception {
        String textWithBackspace = "心\b里";
        String payload = AuroraAgentServiceImpl.buildInnerVoicePayload(
                textWithBackspace, "data:audio/mpeg;base64,Q", "v1");
        JsonNode node = parser.readTree(payload);
        assertEquals(textWithBackspace, node.get("text").asText());
        assertTrue(payload.contains("\\b") || payload.contains("\\u0008"),
                "backspace must be escaped on the wire: " + payload);
    }

    @Test
    void buildInnerVoicePayload_handlesNullTextAsEmptyWithoutCrashing() throws Exception {
        String payload = AuroraAgentServiceImpl.buildInnerVoicePayload(null, "data:audio/mpeg;base64,Q", "v1");
        JsonNode node = parser.readTree(payload);
        assertEquals("", node.get("text").asText());
        assertEquals("v1", node.get("voiceId").asText());
    }
}
