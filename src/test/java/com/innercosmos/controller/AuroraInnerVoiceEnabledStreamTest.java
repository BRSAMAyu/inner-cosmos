package com.innercosmos.controller;

import com.innercosmos.ai.tts.TtsClient;
import com.innercosmos.ai.tts.TtsVoicePreset;
import com.innercosmos.ai.tts.TtsVoicePresets;
import com.innercosmos.config.TestRateLimitConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W1 — with a real (here: fake, deterministic, no network) {@code TtsClient} available, the
 * Aurora SSE turn stream must emit exactly one {@code inner_voice} event carrying {@code text},
 * a {@code data:audio/mpeg;base64,...} {@code audio} field, and the resolved {@code voiceId} --
 * and the turn must still complete normally. Companion to
 * {@link AuroraInnerVoiceDisabledStreamTest}, which proves the event is absent without a
 * provider. The real-network synthesis path is proven separately under
 * {@code evidence/innovation/INNO-INNER-013/}.
 */
@SpringBootTest(properties = {
        "llm.mode=dev",
        "llm.provider=minimax",
        "llm.api-key=",
        "llm.minimax.api-key=",
        "llm.allow-fallback=true",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.datasource.url=jdbc:h2:mem:testinnervoiceenabled;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always"
})
@AutoConfigureMockMvc
@Import({TestRateLimitConfig.class, AuroraInnerVoiceEnabledStreamTest.FakeTtsClientConfig.class})
class AuroraInnerVoiceEnabledStreamTest {

    @Autowired MockMvc mockMvc;

    @Test
    void stream_emitsInnerVoiceEventWithTextAudioAndVoiceIdAtMostOnce() throws Exception {
        MockHttpSession session = registerAndLogin("innervoiceon_");
        long sessionId = createSession(session);
        String body = performStream(session, sessionId, "今天压力好大，感觉快撑不住了");

        assertTrue(body.contains("event:inner_voice"), "expected an inner_voice event; got:\n" + body);
        assertEquals(1, countOccurrences(body, "event:inner_voice"),
                "inner_voice must be emitted at most once per turn; got:\n" + body);
        assertTrue(body.contains("\"audio\":\"data:audio/mpeg;base64,"),
                "inner_voice payload must carry a data-URI audio field; got:\n" + body);
        assertTrue(body.contains("\"voiceId\":\"warm_gentle_female\""),
                "inner_voice payload must carry the resolved (default) voiceId; got:\n" + body);
        assertTrue(body.contains("event:turn.completed"), "turn must still complete normally; got:\n" + body);
    }

    @TestConfiguration
    static class FakeTtsClientConfig {
        @Bean
        @Primary
        TtsClient fakeTtsClient() {
            return new TtsClient() {
                @Override public boolean available() { return true; }
                @Override public List<TtsVoicePreset> voices() { return TtsVoicePresets.ALL; }
                @Override public byte[] synthesize(String text, String voiceId) {
                    return ("FAKE-AUDIO:" + voiceId + ":" + text).getBytes(StandardCharsets.UTF_8);
                }
            };
        }
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private String performStream(MockHttpSession session, long sessionId, String message) throws Exception {
        MvcResult started = mockMvc.perform(get("/api/aurora/stream")
                        .session(session)
                        .param("sessionId", String.valueOf(sessionId))
                        .param("message", message)
                        .param("mode", "DAILY_TALK"))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk());
        return started.getResponse().getContentAsString();
    }

    private MockHttpSession registerAndLogin(String prefix) throws Exception {
        String username = prefix + UUID.randomUUID().toString().substring(0, 8);
        String registerJson = "{\"username\":\"" + username + "\",\"password\":\"testPass123\","
                + "\"nickname\":\"Inner Voice Test\"}";
        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(registerJson))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) regResult.getRequest().getSession(false);
    }

    private long createSession(MockHttpSession session) throws Exception {
        String body = "{\"title\":\"inner voice test\",\"sessionType\":\"AURORA_CHAT\"}";
        MvcResult result = mockMvc.perform(post("/api/dialog/session/create")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        String json = result.getResponse().getContentAsString();
        int idx = json.indexOf("\"id\":");
        assertNotEquals(-1, idx, "session id missing: " + json);
        int start = idx + 5;
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        return Long.parseLong(json.substring(start, end));
    }
}
