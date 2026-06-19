package com.innercosmos.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.config.TestRateLimitConfig;
import com.innercosmos.entity.EmotionTrace;
import com.innercosmos.mapper.EmotionTraceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IC-EMO-002 integration coverage for {@code GET /api/aurora/mood}:
 * - auth required (UNAUTHORIZED when not logged in)
 * - well-formed neutral payload when the user has no EmotionTrace (success=true, no 500)
 * - correct payload shape (emotion + intensity + spectrum + weatherType + gentle label)
 *   when an enriched trace exists.
 */
@SpringBootTest(properties = {
        "llm.mode=dev",
        "llm.provider=minimax",
        "llm.api-key=",
        "llm.minimax.api-key=",
        "llm.allow-fallback=true",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.datasource.url=jdbc:h2:mem:testmood;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always"
})
@AutoConfigureMockMvc
@Import(TestRateLimitConfig.class)
class AuroraMoodControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    EmotionTraceMapper emotionTraceMapper;

    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        session = loginWithUniqueUser();
    }

    @Test
    void mood_requiresAuth() throws Exception {
        // Unauthenticated /api/** is rejected by the security chain (403) before the
        // controller — the endpoint is auth-required, never anonymously readable.
        mockMvc.perform(get("/api/aurora/mood"))
                .andExpect(status().isForbidden());
    }

    @Test
    void mood_noTrace_returnsWellFormedNeutralPayload() throws Exception {
        mockMvc.perform(get("/api/aurora/mood").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.present").value(false))
                .andExpect(jsonPath("$.data.weatherType").value("CLEAR"))
                .andExpect(jsonPath("$.data.spectrum").isArray())
                .andExpect(jsonPath("$.data.gentleLabel").isNotEmpty());
    }

    @Test
    void mood_withEnrichedTrace_returnsFullShape() throws Exception {
        long userId = currentUserId();
        EmotionTrace trace = new EmotionTrace();
        trace.userId = userId;
        trace.emotionName = "平静";
        trace.emotionScore = 4.0;
        trace.weatherType = "SUNNY";
        trace.triggerScene = "日常自我照顾";
        trace.emotionSpectrum = "[{\"emotion\":\"平静\",\"ratio\":0.6},{\"emotion\":\"期待\",\"ratio\":0.4}]";
        trace.analysisSource = "LLM";
        trace.recordDate = LocalDate.now();
        trace.createdAt = LocalDateTime.now();
        trace.updatedAt = LocalDateTime.now();
        emotionTraceMapper.insert(trace);

        mockMvc.perform(get("/api/aurora/mood").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.present").value(true))
                .andExpect(jsonPath("$.data.primaryEmotion").value("平静"))
                .andExpect(jsonPath("$.data.intensity").value(4.0))
                .andExpect(jsonPath("$.data.weatherType").value("SUNNY"))
                .andExpect(jsonPath("$.data.spectrum").isArray())
                .andExpect(jsonPath("$.data.spectrum[0].emotion").value("平静"))
                .andExpect(jsonPath("$.data.gentleLabel").isNotEmpty());
    }

    // ---------------- helpers ----------------

    private long currentUserId() {
        Object value = session.getAttribute("LOGIN_USER_ID");
        return ((Number) value).longValue();
    }

    private MockHttpSession loginWithUniqueUser() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "mooduser_" + suffix;
        String password = "testPass123";

        String registerJson = "{\"username\":\"" + username + "\","
                + "\"password\":\"" + password + "\","
                + "\"nickname\":\"Mood Test\"}";

        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession s = (MockHttpSession) regResult.getRequest().getSession(false);
        if (s == null) {
            String loginJson = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson))
                    .andExpect(status().isOk())
                    .andReturn();
            s = (MockHttpSession) loginResult.getRequest().getSession(false);
        }
        return s;
    }
}
