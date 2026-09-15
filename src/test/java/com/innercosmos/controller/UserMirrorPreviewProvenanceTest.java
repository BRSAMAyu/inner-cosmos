package com.innercosmos.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CP-31 登记项（user-mirror preview 的 LLM 出处信号）的契约测试：响应里的
 * {@code aiGenerated} 必须由生成路径真实携带，controller 只透出、绝不猜测。
 *
 * <p>分支与信号的真实对应（见 CapsulePreviewVO#aiGenerated 的诚实约束）：</p>
 * <ul>
 *   <li><b>有记忆 → true。</b>previewUserMirror 的有记忆分支经
 *       CapsuleAgent#generateUserPersona（purpose CAPSULE_PERSONA_SYNTHESIS）真实发起模型调用；
 *       该方法失败即抛 AI_PROVIDER_ERROR、不会落模板替身，所以 200 返回本身就是"模型调用发起且
 *       成功"的证明。本测试在 dev 环境以 mock provider 运行——断言语义是"走了模型调用路径即为
 *       true"，与 provider 是否 mock 无关（语义与 CapsuleAiGeneratedLabelingTest 一致）。</li>
 *   <li><b>无记忆 → false。</b>空记忆分支用本地固定模板拼装（buildPersonaPrompt 是遗留的本地
 *       字符串拼接，零模型调用），并锚定模板特征防止"标记错分支"。</li>
 *   <li><b>preview-from-memory → false（有记忆也一样）。</b>该端点全程纯规则脱敏，不发起任何
 *       模型调用。</li>
 *   <li><b>纯增量。</b>既有字段（abstractSummary/suggestedPseudonym/personaPromptDraft/
 *       publicTags/removedSensitiveItems/riskWarnings）在两个端点全部保留。</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "llm.mode=dev",
        "llm.provider=mock",
        "llm.allow-fallback=true",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.task.scheduling.enabled=false"
})
@AutoConfigureMockMvc
@Import(com.innercosmos.config.TestRateLimitConfig.class)
class UserMirrorPreviewProvenanceTest {

    /** 本批次专用大号用户 id 段（89xxxxxxx），与其他测试批次不重叠。 */
    private static final java.util.concurrent.atomic.AtomicLong USER_IDS =
            new java.util.concurrent.atomic.AtomicLong(891_000_000L);
    private static final String PASSWORD = "Cp31Mirror#891";
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    // ---------- seeding helpers ----------

    private long seedUser(String tag) {
        long id = USER_IDS.incrementAndGet();
        String username = "cp31mirror_" + tag + "_" + id;
        jdbc.update("INSERT INTO tb_user (id, username, password_hash, nickname, role, status, account_kind, birth_date, age_gate_method) "
                        + "VALUES (?, ?, ?, ?, 'USER', 'ACTIVE', 'HUMAN', '1994-06-01', 'SELF_DECLARED')",
                id, username, ENCODER.encode(PASSWORD), "CP31 mirror " + tag);
        return id;
    }

    private MockHttpSession login(String tag, long id) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cp31mirror_" + tag + "_" + id + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session, "login must establish a session");
        return session;
    }

    private long seedMemory(long owner, String title, String summary) {
        jdbc.update("""
                INSERT INTO tb_memory_card
                    (user_id, title, summary, status, version_no, consent_scope)
                VALUES (?, ?, ?, 'ACTIVE', 1, 'AURORA_PRIVATE')
                """, owner, title, summary);
        return jdbc.queryForObject(
                "SELECT id FROM tb_memory_card WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, owner);
    }

    /** Assert every pre-existing CapsulePreviewVO field still leaves the endpoint (additive-only). */
    private void assertLegacyPreviewFieldsPresent(MvcResult result, String prefix) throws Exception {
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertTrue(data.path("abstractSummary").isTextual() && !data.path("abstractSummary").asText().isBlank(),
                prefix + ": abstractSummary must survive");
        assertTrue(data.path("suggestedPseudonym").isTextual() && !data.path("suggestedPseudonym").asText().isBlank(),
                prefix + ": suggestedPseudonym must survive");
        assertTrue(data.path("personaPromptDraft").isTextual() && !data.path("personaPromptDraft").asText().isBlank(),
                prefix + ": personaPromptDraft must survive");
        assertTrue(data.path("publicTags").isArray(), prefix + ": publicTags must survive");
        assertTrue(data.path("removedSensitiveItems").isArray(), prefix + ": removedSensitiveItems must survive");
        assertTrue(data.path("riskWarnings").isArray(), prefix + ": riskWarnings must survive");
    }

    // ---------- user-mirror/preview: the two real branches ----------

    @Test
    @DisplayName("有记忆用户：走 CapsuleAgent.generateUserPersona 模型调用路径 → aiGenerated=true（mock provider 也算，语义是走了模型调用）")
    void userMirrorPreviewWithMemoriesIsLabeledAiFromTheModelCallPath() throws Exception {
        long ownerId = seedUser("llm");
        MockHttpSession owner = login("llm", ownerId);
        seedMemory(ownerId, "关于深夜项目的记忆", "连续三周赶同一个交付，压力累积但也在意成果。");

        MvcResult result = mockMvc.perform(post("/api/capsule/user-mirror/preview").session(owner))
                .andExpect(status().isOk())
                // The LLM branch cannot return 200 unless generateUserPersona's provider call
                // succeeded (failure throws AI_PROVIDER_ERROR) — so this boolean is carried by
                // the generation path, not guessed from "user has memories".
                .andExpect(jsonPath("$.data.aiGenerated").value(true))
                .andReturn();
        assertLegacyPreviewFieldsPresent(result, "user-mirror/preview (LLM branch)");
        // Sanity: true is not vacuous — the draft actually carries generated persona text.
        String draft = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("personaPromptDraft").asText();
        assertTrue(draft.contains("透明提示"), "the LLM-branch draft carries the platform transparency suffix");
    }

    @Test
    @DisplayName("无记忆用户：固定模板分支（零模型调用）→ aiGenerated=false，且锚定模板特征")
    void userMirrorPreviewWithoutMemoriesIsTemplateNotAi() throws Exception {
        long ownerId = seedUser("empty");
        MockHttpSession owner = login("empty", ownerId);
        // No memories seeded — previewUserMirror must take the local template branch.

        MvcResult result = mockMvc.perform(post("/api/capsule/user-mirror/preview").session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiGenerated").value(false))
                .andExpect(jsonPath("$.data.suggestedPseudonym").value("新的回声分身"))
                .andReturn();
        assertLegacyPreviewFieldsPresent(result, "user-mirror/preview (template branch)");
        // Pin the template branch itself (CapsuleAgent#buildPersonaPrompt is local string assembly).
        String draft = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("personaPromptDraft").asText();
        assertTrue(draft.contains("新的回声分身"), "the template draft names the template pseudonym");
        assertFalse(draft.contains("透明提示"), "the LLM-branch-only transparency suffix must not appear");
    }

    // ---------- same-chain egress: preview-from-memory is pure rules ----------

    @Test
    @DisplayName("preview-from-memory：有记忆也全程纯规则（零模型调用）→ aiGenerated=false；空 memoryIds 同样 false")
    void previewFromMemoryIsNeverLabeledAiEvenWithMemories() throws Exception {
        long ownerId = seedUser("rules");
        MockHttpSession owner = login("rules", ownerId);
        long memoryId = seedMemory(ownerId, "关于晨跑的记忆", "连续早起跑步，状态在慢慢回升。");

        MvcResult withMemories = mockMvc.perform(post("/api/capsule/preview-from-memory")
                        .session(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memoryIds\":[" + memoryId + "],\"privacyLevel\":\"STRICT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiGenerated").value(false))
                .andReturn();
        assertLegacyPreviewFieldsPresent(withMemories, "preview-from-memory (with memories)");
        // Pin the rule-generated template (DataMaskingServiceImpl#generatePersonaPrompt is local).
        String draft = objectMapper.readTree(withMemories.getResponse().getContentAsString())
                .path("data").path("personaPromptDraft").asText();
        assertTrue(draft.contains("共鸣体"), "the rule-template draft must be present");

        mockMvc.perform(post("/api/capsule/preview-from-memory")
                        .session(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiGenerated").value(false))
                .andExpect(jsonPath("$.data.personaPromptDraft").value("这是一个空的共鸣体预览."));
    }
}
