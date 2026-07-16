package com.innercosmos.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.common.Constants;
import com.innercosmos.entity.User;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.mapper.PsychologySkillReleaseMapper;
import com.innercosmos.entity.PsychologySkillRelease;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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
        "spring.datasource.url=jdbc:h2:mem:psychology-release;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always", "inner-cosmos.safety.semantic-recheck.enabled=false", "llm.provider=mock"
})
@AutoConfigureMockMvc
class PsychologySkillReleaseControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserMapper userMapper;
    @Autowired PsychologySkillReleaseMapper releaseMapper;

    @Test
    void previewMustPassMachineAndHumanGatesAndCanBeDisabledAndRolledBack() throws Exception {
        MockHttpSession demo = session("demo");
        MockHttpSession river = session("river");
        MockHttpSession admin = session("admin");
        String id = "emotion-needs-clarifier";
        String version = "1.0.0";
        Map<String, Object> run = Map.of(
                "explicitConsent", true, "retentionChoice", "DISCARD_AFTER_SESSION", "locale", "zh-CN",
                "consentScopes", new String[]{"current-run-input"},
                "answers", Map.of("situation", "评审", "feeling", "紧张", "need", "准备"));

        mockMvc.perform(post("/api/psychology/skills/{id}/runs", id).session(demo)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(run)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.manifestHash").isString());
        mockMvc.perform(get("/api/admin/psychology/skills/releases").session(river))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/admin/psychology/skills/releases").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[1].skillId").value("emotion-needs-clarifier"))
                .andExpect(jsonPath("$.data[1].releaseStatus").value("LIMITED_PREVIEW"));
        mockMvc.perform(post("/api/admin/psychology/skills/releases/{id}/{version}/publish", id, version).session(admin))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/psychology/skills/releases/{id}/{version}/review", id, version).session(admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"independent human review record\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.reviewStatus").value("HUMAN_REVIEWED"))
                .andExpect(jsonPath("$.data.reviewedByUserId").value(user("admin").id));
        mockMvc.perform(post("/api/admin/psychology/skills/releases/{id}/{version}/publish", id, version).session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.releaseStatus").value("PUBLISHED"));
        mockMvc.perform(post("/api/admin/psychology/skills/releases/{id}/{version}/disable", id, version).session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"safety stop drill\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(false));
        mockMvc.perform(post("/api/psychology/skills/{id}/runs", id).session(demo)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(run)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/psychology/skills/releases/{id}/{version}/rollback", id, version).session(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"verified recovery drill\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(true));
        mockMvc.perform(post("/api/psychology/skills/{id}/runs", id).session(demo)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(run)))
                .andExpect(status().isOk());
        releaseMapper.update(null, new UpdateWrapper<PsychologySkillRelease>()
                .eq("skill_id", id).eq("skill_version", version).set("manifest_hash", "0".repeat(64)));
        mockMvc.perform(post("/api/psychology/skills/{id}/runs", id).session(demo)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(run)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("未升版本")));
    }

    private MockHttpSession session(String username) {
        User user = user(username);
        assertThat(user).isNotNull();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(Constants.SESSION_USER_KEY, user.id);
        return session;
    }

    private User user(String username) {
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        assertThat(user).isNotNull();
        return user;
    }
}
