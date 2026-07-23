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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Owner-scoped TTS voice + Aurora inner-voice (心声) preference contract: fixed preset catalog,
 * defaults for a brand-new user (no profile row yet), and PATCH persistence. {@code tts.enabled}
 * is false in this test's default config (no real credential in CI), so this suite exercises the
 * REST/persistence contract against {@code DisabledTtsClient}, not real synthesis -- see
 * {@code evidence/innovation/INNO-INNER-013/} for the real-provider proof.
 */
@SpringBootTest(properties = {
        "llm.mode=dev",
        "llm.provider=mock",
        "llm.allow-fallback=true",
        "spring.datasource.url=jdbc:h2:mem:testtts;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always"
})
@AutoConfigureMockMvc
@Import(TestRateLimitConfig.class)
class TtsControllerTest {

    @Autowired MockMvc mockMvc;

    @Test
    void voices_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/me/tts/voices"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void voices_returnsFixedCatalogAndDefaultsForFreshUser() throws Exception {
        MockHttpSession session = register("tts_" + UUID.randomUUID().toString().substring(0, 8));

        mockMvc.perform(get("/api/me/tts/voices").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.voices").isArray())
                .andExpect(jsonPath("$.data.voices.length()").value(6))
                .andExpect(jsonPath("$.data.voices[0].id").isNotEmpty())
                .andExpect(jsonPath("$.data.voices[0].label").isNotEmpty())
                .andExpect(jsonPath("$.data.voices[0].language").value("zh"))
                .andExpect(jsonPath("$.data.voices[0].previewText").isNotEmpty())
                .andExpect(jsonPath("$.data.currentVoiceId").value("warm_gentle_female"))
                .andExpect(jsonPath("$.data.innerVoiceEnabled").value(true))
                .andExpect(jsonPath("$.data.innerVoiceMode").value("AMBIENT"));
    }

    @Test
    void preferences_patchPersistsAndReflectsOnNextGet() throws Exception {
        MockHttpSession session = register("tts_" + UUID.randomUUID().toString().substring(0, 8));

        mockMvc.perform(patch("/api/me/tts/preferences").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voiceId\":\"deep_soothing_male\",\"innerVoiceEnabled\":false,\"innerVoiceMode\":\"ON_DEMAND\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVoiceId").value("deep_soothing_male"))
                .andExpect(jsonPath("$.data.innerVoiceEnabled").value(false))
                .andExpect(jsonPath("$.data.innerVoiceMode").value("ON_DEMAND"));

        mockMvc.perform(get("/api/me/tts/voices").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentVoiceId").value("deep_soothing_male"))
                .andExpect(jsonPath("$.data.innerVoiceEnabled").value(false))
                .andExpect(jsonPath("$.data.innerVoiceMode").value("ON_DEMAND"));
    }

    @Test
    void preferences_rejectsUnknownVoiceId() throws Exception {
        MockHttpSession session = register("tts_" + UUID.randomUUID().toString().substring(0, 8));

        mockMvc.perform(patch("/api/me/tts/preferences").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voiceId\":\"does-not-exist\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void preferences_rejectsUnknownMode() throws Exception {
        MockHttpSession session = register("tts_" + UUID.randomUUID().toString().substring(0, 8));

        mockMvc.perform(patch("/api/me/tts/preferences").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"innerVoiceMode\":\"SOMETIMES\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void preview_withoutRealProviderReturnsBusinessError() throws Exception {
        MockHttpSession session = register("tts_" + UUID.randomUUID().toString().substring(0, 8));

        // tts.enabled defaults to false in this test's application config (no real credential
        // wired into CI), so preview must fail with a clean business error, never a 500.
        mockMvc.perform(post("/api/me/tts/preview").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"voiceId\":\"warm_gentle_female\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI_PROVIDER_ERROR"));
    }

    private MockHttpSession register(String username) throws Exception {
        String json = "{\"username\":\"" + username + "\",\"password\":\"testPass123\","
                + "\"nickname\":\"TTS Test\"}";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
