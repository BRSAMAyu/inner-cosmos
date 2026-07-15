package com.innercosmos.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.common.Constants;
import com.innercosmos.entity.PsychologySkillRun;
import com.innercosmos.entity.User;
import com.innercosmos.mapper.PsychologySkillRunMapper;
import com.innercosmos.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:psychology-skills;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always",
        "inner-cosmos.safety.semantic-recheck.enabled=false",
        "llm.provider=mock"
})
@AutoConfigureMockMvc
class PsychologySkillControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserMapper userMapper;
    @Autowired PsychologySkillRunMapper runMapper;

    @Test
    void manifestRunRetentionAndOwnerScopedRevocationWorkEndToEnd() throws Exception {
        MockHttpSession demo = session("demo");
        MockHttpSession river = session("river");

        mockMvc.perform(get("/api/psychology/skills").session(demo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].userInvocation").value("EXPLICIT_CONSENT"));

        Map<String, Object> body = Map.of(
                "explicitConsent", true,
                "retentionChoice", "SAVE_RESULT",
                "locale", "zh-CN",
                "consentScopes", new String[]{"current-run-input"},
                "answers", Map.of("situation", "周会临近", "feeling", "紧张", "need", "准备感"));
        String response = mockMvc.perform(post("/api/psychology/skills/emotion-needs-clarifier/runs")
                        .session(demo).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.result.confidence").value("REFLECTIVE_NOT_DIAGNOSTIC"))
                .andReturn().getResponse().getContentAsString();
        long runId = objectMapper.readTree(response).path("data").path("id").asLong();

        PsychologySkillRun stored = runMapper.selectById(runId);
        assertThat(stored.inputFingerprint).hasSize(64).doesNotContain("周会", "紧张");
        assertThat(stored.consentScopes).isEqualTo("current-run-input");
        assertThat(stored.evidenceRefs).contains("Lieberman");

        mockMvc.perform(post("/api/psychology/skills/runs/{id}/revoke", runId).session(river))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        mockMvc.perform(post("/api/psychology/skills/runs/{id}/revoke", runId).session(demo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"))
                .andExpect(jsonPath("$.data.result").isEmpty());
        assertThat(runMapper.selectById(runId).resultJson).isNull();
    }

    @Test
    void noConsentFailsAndCrisisInputStopsOrdinaryReflectionWithoutRetainingText() throws Exception {
        MockHttpSession demo = session("demo");
        Map<String, Object> withoutConsent = Map.of(
                "explicitConsent", false, "retentionChoice", "SAVE_RESULT", "locale", "zh-CN",
                "consentScopes", new String[]{"current-run-input"},
                "answers", Map.of("decision", "是否出门", "pullToward", "见朋友", "pullAway", "疲惫"));
        mockMvc.perform(post("/api/psychology/skills/decision-conflict-map/runs")
                        .session(demo).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(withoutConsent)))
                .andExpect(status().isBadRequest());

        Map<String, Object> crisis = Map.of(
                "explicitConsent", true, "retentionChoice", "SAVE_RESULT", "locale", "zh-CN",
                "consentScopes", new String[]{"current-run-input"},
                "answers", Map.of("situation", "今晚", "feeling", "我想自杀", "need", "结束痛苦"));
        String response = mockMvc.perform(post("/api/psychology/skills/emotion-needs-clarifier/runs")
                        .session(demo).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(crisis)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ESCALATED"))
                .andExpect(jsonPath("$.data.escalationCode").value("LOCAL_CRISIS_RESOURCES"))
                .andExpect(jsonPath("$.data.retentionChoice").value("DISCARD_AFTER_SESSION"))
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(response).path("data");
        PsychologySkillRun stored = runMapper.selectById(data.path("id").asLong());
        assertThat(stored.resultJson).isNull();
        assertThat(stored.inputFingerprint).doesNotContain("自杀");
    }

    private MockHttpSession session(String username) {
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        assertThat(user).isNotNull();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(Constants.SESSION_USER_KEY, user.id);
        return session;
    }
}
