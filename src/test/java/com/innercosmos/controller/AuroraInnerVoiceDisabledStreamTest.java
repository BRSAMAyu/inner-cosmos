package com.innercosmos.controller;

import com.innercosmos.config.TestRateLimitConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W1 — with no real TTS provider configured (the default: {@code tts.enabled=false}), the Aurora
 * SSE turn stream must NEVER emit {@code inner_voice} and the turn must still complete cleanly.
 * Companion to {@link AuroraInnerVoiceEnabledStreamTest}, which proves the event DOES fire with a
 * fake available {@code TtsClient}.
 */
@SpringBootTest(properties = {
        "llm.mode=dev",
        "llm.provider=minimax",
        "llm.api-key=",
        "llm.minimax.api-key=",
        "llm.allow-fallback=true",
        "tts.enabled=false",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.datasource.url=jdbc:h2:mem:testinnervoicedisabled;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always"
})
@AutoConfigureMockMvc
@Import(TestRateLimitConfig.class)
class AuroraInnerVoiceDisabledStreamTest {

    @Autowired MockMvc mockMvc;

    @Test
    void stream_neverEmitsInnerVoiceEventWhenNoProviderConfigured() throws Exception {
        MockHttpSession session = registerAndLogin("innervoiceoff_");
        long sessionId = createSession(session);
        String body = performStream(session, sessionId, "今天有点累，想聊聊");

        // The dual-kernel runtime still composes an inner-voice TEXT internally (best-effort),
        // but with no real TtsClient the turn must complete cleanly with NO inner_voice event.
        assertFalse(body.contains("event:inner_voice"),
                "no inner_voice event may be emitted when tts.enabled=false; got:\n" + body);
        assertTrue(body.contains("event:turn.completed"), "turn must still complete normally; got:\n" + body);
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
