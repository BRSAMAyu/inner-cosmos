package com.innercosmos.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W1 capsule-voice reuse: integration contract for {@code POST /api/persona-chat/session/{id}/voice}.
 *
 * <p>With {@code tts.enabled=false} (the CI default -- no real credential wired in), the endpoint
 * must fail with a clean {@code AI_PROVIDER_ERROR}, NEVER a raw 500 -- this is the REST analogue of
 * Aurora's inner-voice "omit on failure": the chat itself is never affected. The happy-path
 * synthesis (real bytes, distinct capsule voice) is proven by {@code PersonaChatServiceImplSynthesizeVoiceTest}
 * (stubbed client) and {@code QwenAudioTtsClientCapsuleVoiceRealProviderTest} (real network).
 */
@SpringBootTest(properties = {
        "llm.mode=dev",
        "llm.provider=mock",
        "llm.allow-fallback=true",
        "spring.datasource.url=jdbc:h2:mem:testcapsulevoice;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always"
})
@AutoConfigureMockMvc
@Import(com.innercosmos.config.TestRateLimitConfig.class)
class PersonaChatVoiceControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        session = register("voice_" + UUID.randomUUID().toString().substring(0, 8));
    }

    @Test
    @DisplayName("voice requires auth (401 when anonymous)")
    void voice_requiresAuth() throws Exception {
        mockMvc.perform(post("/api/persona-chat/session/1/voice"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("voice returns AI_PROVIDER_ERROR (not 500) when TTS is not configured")
    void voice_unavailableProvider_returnsCleanError() throws Exception {
        long userId = currentUserId();
        long capsuleId = seedPublicCapsule(userId);
        long sessionId = seedSession(userId, capsuleId);
        seedCapsuleReply(sessionId, "我是来自另一个人的回声。");

        mockMvc.perform(post("/api/persona-chat/session/" + sessionId + "/voice").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AI_PROVIDER_ERROR"));
    }

    @Test
    @DisplayName("voice refuses a withdrawn capsule (reuses the published-capsule gate)")
    void voice_withdrawnCapsule_returnsCapsuleWithdrawn() throws Exception {
        long userId = currentUserId();
        long capsuleId = seedWithdrawnCapsule(userId);
        long sessionId = seedSession(userId, capsuleId);
        seedCapsuleReply(sessionId, "本应听不到的回声。");

        mockMvc.perform(post("/api/persona-chat/session/" + sessionId + "/voice").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAPSULE_WITHDRAWN"));
    }

    @Test
    @DisplayName("voice returns NOT_FOUND when the session has no capsule reply yet")
    void voice_noReply_returnsNotFound() throws Exception {
        long userId = currentUserId();
        long capsuleId = seedPublicCapsule(userId);
        long sessionId = seedSession(userId, capsuleId);

        mockMvc.perform(post("/api/persona-chat/session/" + sessionId + "/voice").session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ---------------- helpers ----------------

    private long currentUserId() {
        Object value = session.getAttribute("LOGIN_USER_ID");
        return ((Number) value).longValue();
    }

    private long seedPublicCapsule(long userId) {
        jdbc.update("INSERT INTO tb_echo_capsule (owner_user_id, capsule_type, pseudonym, intro, "
                + "visibility_status, is_public) VALUES (?, ?, ?, ?, ?, ?)",
                userId, "USER_CAPSULE", "test-echo", "intro", "PUBLIC", true);
        return jdbc.queryForObject("SELECT id FROM tb_echo_capsule WHERE owner_user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, userId);
    }

    private long seedWithdrawnCapsule(long userId) {
        jdbc.update("INSERT INTO tb_echo_capsule (owner_user_id, capsule_type, pseudonym, intro, "
                + "visibility_status, is_public) VALUES (?, ?, ?, ?, ?, ?)",
                userId, "USER_CAPSULE", "withdrawn-echo", "intro", "WITHDRAWN", false);
        return jdbc.queryForObject("SELECT id FROM tb_echo_capsule WHERE owner_user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, userId);
    }

    private long seedSession(long userId, long capsuleId) {
        jdbc.update("INSERT INTO tb_persona_chat_session (visitor_user_id, capsule_id, status, turn_count, daily_limit) "
                + "VALUES (?, ?, 'ACTIVE', 1, 30)", userId, capsuleId);
        return jdbc.queryForObject("SELECT id FROM tb_persona_chat_session WHERE visitor_user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, userId);
    }

    private void seedCapsuleReply(long sessionId, String text) {
        jdbc.update("INSERT INTO tb_persona_chat_message (session_id, sender_type, text_content) VALUES (?, 'CAPSULE', ?)",
                sessionId, text);
    }

    private MockHttpSession register(String username) throws Exception {
        String json = "{\"username\":\"" + username + "\",\"password\":\"testPass123\","
                + "\"nickname\":\"Voice Test\"}";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
