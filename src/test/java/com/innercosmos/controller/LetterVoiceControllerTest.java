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
 * W1 slow-letter voice reuse: integration contract for {@code POST /api/letters/{id}/voice}.
 *
 * <p>With {@code tts.enabled=false} (the CI default -- no real credential wired in), the endpoint
 * must fail with a clean {@code AI_PROVIDER_ERROR}, NEVER a raw 500 -- this is the REST analogue of
 * the capsule-voice / Aurora inner-voice "omit on failure" contract: the letter itself is never
 * affected. The recipient-only authorization and the delivered-state gate are also pinned here at
 * the HTTP layer; the happy-path synthesis (real bytes, warm voice) is proven by
 * {@code SlowLetterServiceImplSynthesizeVoiceTest} (stubbed client).
 */
@SpringBootTest(properties = {
        "llm.mode=dev",
        "llm.provider=mock",
        "llm.allow-fallback=true",
        "spring.datasource.url=jdbc:h2:mem:testlettervoice;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always"
})
@AutoConfigureMockMvc
@Import(com.innercosmos.config.TestRateLimitConfig.class)
class LetterVoiceControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    private MockHttpSession recipientSession;
    private long recipientId;
    private long senderId;

    @BeforeEach
    void setUp() throws Exception {
        recipientSession = register("letter_voice_" + UUID.randomUUID().toString().substring(0, 8));
        recipientId = currentUserId(recipientSession);
        // A second registered user plays the letter's sender (sender_user_id != receiver_user_id).
        senderId = currentUserId(register("letter_voice_snd_" + UUID.randomUUID().toString().substring(0, 8)));
    }

    @Test
    @DisplayName("voice requires auth (401 when anonymous)")
    void voice_requiresAuth() throws Exception {
        mockMvc.perform(post("/api/letters/1/voice"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("voice returns AI_PROVIDER_ERROR (not 500) when TTS is not configured, for a delivered letter")
    void voice_unavailableProvider_returnsCleanError() throws Exception {
        long letterId = seedLetter(recipientId, "DELIVERED", "我读到你把夕阳当作恢复资源那段。");

        mockMvc.perform(post("/api/letters/" + letterId + "/voice").session(recipientSession))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AI_PROVIDER_ERROR"));
    }

    @Test
    @DisplayName("voice refuses a letter still in flight (reuses the delivered-to-recipient delivery-state gate)")
    void voice_inFlightLetter_returnsLetterStateInvalid() throws Exception {
        long letterId = seedLetter(recipientId, "FLYING", "还没抵达的正文。");

        mockMvc.perform(post("/api/letters/" + letterId + "/voice").session(recipientSession))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LETTER_STATE_INVALID"));
    }

    @Test
    @DisplayName("voice refuses a non-recipient (reuses the recipient-scoped letter-read gate)")
    void voice_nonRecipient_returnsUnauthorized() throws Exception {
        long letterId = seedLetter(recipientId, "DELIVERED", "只有收件人能听这封信。");
        // A different registered user is neither the sender nor the recipient of this letter.
        MockHttpSession otherSession = register("letter_voice_other_" + UUID.randomUUID().toString().substring(0, 8));

        mockMvc.perform(post("/api/letters/" + letterId + "/voice").session(otherSession))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // ---------------- helpers ----------------

    private long currentUserId(MockHttpSession session) {
        Object value = session.getAttribute("LOGIN_USER_ID");
        return ((Number) value).longValue();
    }

    /** Inserts a letter addressed TO the recipient from the registered sender, in the given status. */
    private long seedLetter(long recipientId, String status, String body) {
        jdbc.update("INSERT INTO tb_slow_letter (sender_user_id, receiver_user_id, title, letter_body, status, parallax_distance) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                senderId, recipientId, "一封测试慢信", body, status, 3);
        return jdbc.queryForObject("SELECT id FROM tb_slow_letter WHERE receiver_user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, recipientId);
    }

    private MockHttpSession register(String username) throws Exception {
        String json = "{\"username\":\"" + username + "\",\"password\":\"testPass123\","
                + "\"nickname\":\"Letter Voice Test\"}";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
