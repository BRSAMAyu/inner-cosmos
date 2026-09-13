package com.innercosmos.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CP-31 / closing-checklist §2-9: explicit AI-generated content labeling on every surface
 * where capsule-derived content flows out — owner detail/list, context preview, plaza
 * list/matches, visitor persona-chat replies and the data-export package.
 *
 * <p>Honesty contract under test (the same tiers documented on
 * {@code com.innercosmos.vo.CapsuleAiLabeling}):</p>
 * <ul>
 *   <li>personaPrompt is LLM-compiled for user capsules → aiGenerated=true /
 *       aiGeneratedFields=[personaPrompt];</li>
 *   <li>contextPreviewJson/styleProfileJson are deterministically system-compiled from the
 *       owner's authorized memories (rule-based compiler, NO LLM call — verified against
 *       CapsuleServiceImpl#buildContextPreview / #inferStyleProfile) → they appear in
 *       systemCompiledFields, never in aiGeneratedFields;</li>
 *   <li>owner-written text (pseudonym/intro/publicTags/ownerContextNote) is never rounded up
 *       to AI — negative assertions pin that;</li>
 *   <li>SEED_CAPSULE persona is a platform hand-authored template → no AI claim at all.</li>
 * </ul>
 *
 * <p>Cache note (task §3, honest re-check): the capsule read path has NO cache layer — no
 * @Cacheable/Caffeine/CacheManager anywhere in src/main and no in-memory derived-view state
 * in the capsule services (CP-14 audit re-confirmed). No cache, so there is no invalidation
 * surface to test here; the PERSISTED derivative (capsule match vector) is physically erased
 * on archive/withdraw via CapsuleEmbeddingIndexService#retireForCapsule, covered by existing
 * retraction tests — read-only confirmed, not retested here.</p>
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
class CapsuleAiGeneratedLabelingTest {

    /** CP-31 dedicated large user id segment (95xxxxxxx) — no overlap with other batches. */
    private static final java.util.concurrent.atomic.AtomicLong USER_IDS =
            new java.util.concurrent.atomic.AtomicLong(951_000_000L);
    private static final String PASSWORD = "Cp31Label#951";
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    // ---------- seeding helpers ----------

    private long seedUser(String tag) {
        long id = USER_IDS.incrementAndGet();
        String username = "cp31_" + tag + "_" + id;
        jdbc.update("INSERT INTO tb_user (id, username, password_hash, nickname, role, status, account_kind, birth_date, age_gate_method) "
                        + "VALUES (?, ?, ?, ?, 'USER', 'ACTIVE', 'HUMAN', '1994-06-01', 'SELF_DECLARED')",
                id, username, ENCODER.encode(PASSWORD), "CP31 " + tag);
        return id;
    }

    private MockHttpSession login(String tag, long id) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cp31_" + tag + "_" + id + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session, "login must establish a session");
        return session;
    }

    /** Create a PUBLIC user capsule through the real endpoint (mock LLM persona compile). */
    private long createPublicCapsule(MockHttpSession session, String pseudonym) throws Exception {
        String payload = "{\"pseudonym\":\"" + pseudonym + "\","
                + "\"intro\":\"owner 手写的介绍，不是 AI 文案。\","
                + "\"ownerContextNote\":\"owner 手写的语境备注。\","
                + "\"publicTags\":[\"cp31-labeling\"],"
                + "\"memoryIds\":[],"
                + "\"allowTopics\":[\"自我观察\"],\"blockedTopics\":[\"私人身份\"],"
                + "\"maxConversationTurns\":7,\"allowLetterRequest\":true,"
                + "\"privacyLevel\":\"STRICT\",\"visibilityStatus\":\"PUBLIC\",\"isPublic\":true}";
        MvcResult created = mockMvc.perform(post("/api/capsule/create-from-memory")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        // Explicit UTF-8 bytes: MockMvc's content(String) legacy default is
                        // ISO-8859-1, which would mangle the Chinese owner-written text that
                        // the exact-match assertions below rely on.
                        .content(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private void assertOwnerLabelsPresentOnCapsulePayload(MvcResult result, String prefix) throws Exception {
        String body = result.getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        assertTrue(data.path("aiGenerated").isBoolean(), prefix + ": aiGenerated must be a boolean");
        assertTrue(data.path("aiGenerated").asBoolean(), prefix + ": a compiled user capsule carries LLM persona text");
        assertEquals(List.of("personaPrompt"), toList(data.path("aiGeneratedFields")),
                prefix + ": exactly personaPrompt is LLM-written");
        assertEquals(List.of("contextPreviewJson", "styleProfileJson"), toList(data.path("systemCompiledFields")),
                prefix + ": compiled-but-not-LLM fields are named as system-compiled, not AI");
        assertTrue(containsAll(toList(data.path("ownerWrittenFields")),
                "pseudonym", "intro", "publicTags", "ownerContextNote"),
                prefix + ": owner-written fields are enumerated");
        // Negative honesty checks: owner hand-written text is never rounded up to AI.
        List<String> aiFields = toList(data.path("aiGeneratedFields"));
        for (String ownerField : List.of("pseudonym", "intro", "publicTags", "ownerContextNote")) {
            assertFalse(aiFields.contains(ownerField),
                    prefix + ": owner-written " + ownerField + " must not be labeled AI");
        }
        assertFalse(data.path("personaPrompt").asText("").isBlank(),
                prefix + ": the LLM persona prompt must actually be present for aiGenerated=true to be honest");
    }

    private static List<String> toList(JsonNode node) {
        List<String> values = new ArrayList<>();
        node.forEach(item -> values.add(item.asText()));
        return values;
    }

    private static boolean containsAll(List<String> actual, String... expected) {
        return actual.containsAll(List.of(expected));
    }

    // ---------- §2 surfaces: owner detail / list / create echo ----------

    @Test
    void ownerCapsulePayloadsCarryFieldLevelAiLabels() throws Exception {
        long ownerId = seedUser("owner");
        MockHttpSession owner = login("owner", ownerId);
        long capsuleId = createPublicCapsule(owner, "cp31-标注载体");

        MvcResult detail = mockMvc.perform(get("/api/capsule/" + capsuleId).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value((int) capsuleId))
                .andReturn();
        assertOwnerLabelsPresentOnCapsulePayload(detail, "GET /api/capsule/{id}");

        MvcResult mine = mockMvc.perform(get("/api/capsule/my").session(owner))
                .andExpect(status().isOk())
                .andReturn();
        boolean found = false;
        for (JsonNode item : objectMapper.readTree(mine.getResponse().getContentAsString()).path("data")) {
            if (item.path("id").asLong() == capsuleId) {
                found = true;
                assertTrue(item.path("aiGenerated").asBoolean(), "GET /api/capsule/my item must be labeled");
                assertEquals(List.of("personaPrompt"), toList(item.path("aiGeneratedFields")),
                        "GET /api/capsule/my item AI fields");
            }
        }
        assertTrue(found, "GET /api/capsule/my must contain the created capsule");
    }

    // ---------- context preview: compiled fields named as system, never AI ----------

    @Test
    void contextPreviewLabelsCompiledFieldsAsSystemNotAi() throws Exception {
        long ownerId = seedUser("ctx");
        MockHttpSession owner = login("ctx", ownerId);
        long capsuleId = createPublicCapsule(owner, "cp31-语境预览");

        mockMvc.perform(get("/api/capsule/" + capsuleId + "/context-preview").session(owner))
                .andExpect(status().isOk())
                // No LLM-written field is part of this payload — personaPrompt is not in the view.
                .andExpect(jsonPath("$.data.personaPrompt").doesNotExist())
                .andExpect(jsonPath("$.data.aiGenerated").value(false))
                .andExpect(jsonPath("$.data.aiGeneratedFields").value(empty()))
                .andExpect(jsonPath("$.data.systemCompiledFields")
                        .value(org.hamcrest.Matchers.contains("contextPreview", "styleProfile")))
                .andExpect(jsonPath("$.data.ownerWrittenFields")
                        .value(hasItems("pseudonym", "ownerContextNote", "publicTags")))
                .andExpect(jsonPath("$.data.aiLabelingNote").isNotEmpty());
    }

    // ---------- plaza list / matches: public projections carry no AI fields ----------

    @Test
    void plazaListAndMatchesNeverFlagPublicOwnerTextAsAi() throws Exception {
        long ownerId = seedUser("plazaOwner");
        MockHttpSession owner = login("plazaOwner", ownerId);
        String pseudonym = "cp31-广场-" + ownerId;
        createPublicCapsule(owner, pseudonym);

        long visitorId = seedUser("plazaVisitor");
        MockHttpSession visitor = login("plazaVisitor", visitorId);

        // Every plaza row is the public-safe VO: owner-written text + platform scores only.
        mockMvc.perform(get("/api/plaza/capsules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.pseudonym=='" + pseudonym + "')]").isNotEmpty())
                .andExpect(jsonPath("$.data[*].aiGenerated", org.hamcrest.Matchers.everyItem(equalTo(false))))
                .andExpect(jsonPath("$.data[*].aiGeneratedFields", org.hamcrest.Matchers.everyItem(empty())));

        // Match items expose the same public-safe VO plus rule-computed scores — no AI content.
        mockMvc.perform(get("/api/plaza/matches").session(visitor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].aiGenerated", org.hamcrest.Matchers.everyItem(equalTo(false))))
                .andExpect(jsonPath("$.data[*].aiGeneratedFields", org.hamcrest.Matchers.everyItem(empty())))
                .andExpect(jsonPath("$.data[*].capsule.personaPrompt").doesNotExist());
    }

    // ---------- visitor persona chat: AI replies labeled, visitor text never ----------

    @Test
    void visitorChatRepliesAreLabeledAiAndVisitorMessagesAreNot() throws Exception {
        long ownerId = seedUser("chatOwner");
        MockHttpSession owner = login("chatOwner", ownerId);
        long capsuleId = createPublicCapsule(owner, "cp31-访客对话");

        long visitorId = seedUser("chatVisitor");
        MockHttpSession visitor = login("chatVisitor", visitorId);

        MvcResult sessionCreated = mockMvc.perform(post("/api/persona-chat/session/create")
                        .session(visitor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capsuleId\":" + capsuleId + "}"))
                .andExpect(status().isOk())
                .andReturn();
        long sessionId = objectMapper.readTree(sessionCreated.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        String message = "{\"sessionId\":" + sessionId + ",\"message\":\"想听听你如何看待自责\"}";
        mockMvc.perform(post("/api/persona-chat/message")
                        .session(visitor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(message.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.senderType").value("CAPSULE"))
                .andExpect(jsonPath("$.data.textContent").isNotEmpty())
                .andExpect(jsonPath("$.data.aiGenerated").value(true));

        MvcResult messages = mockMvc.perform(get("/api/persona-chat/session/" + sessionId + "/messages").session(visitor))
                .andExpect(status().isOk())
                .andReturn();
        boolean capsuleReplyLabeledAi = false;
        boolean visitorMessageLabeledHuman = false;
        for (JsonNode entry : objectMapper.readTree(messages.getResponse().getContentAsString()).path("data")) {
            if ("CAPSULE".equals(entry.path("senderType").asText())) {
                assertTrue(entry.path("aiGenerated").asBoolean(),
                        "a capsule (LLM) reply must be labeled aiGenerated=true");
                capsuleReplyLabeledAi = true;
            }
            if ("VISITOR".equals(entry.path("senderType").asText())) {
                assertFalse(entry.path("aiGenerated").asBoolean(),
                        "the visitor's own message must never be labeled AI");
                visitorMessageLabeledHuman = true;
            }
        }
        assertTrue(capsuleReplyLabeledAi, "the session history must contain the labeled capsule reply");
        assertTrue(visitorMessageLabeledHuman, "the session history must contain the labeled visitor message");
    }

    // ---------- export package: per-record field-level labels + SEED negative ----------

    @Test
    void exportPackageLabelsEchoCapsuleRecordsPerFieldAndSeedTemplatesAreNotAi() throws Exception {
        long ownerId = seedUser("export");
        MockHttpSession owner = login("export", ownerId);
        long userCapsuleId = createPublicCapsule(owner, "cp31-导出");

        // A SEED capsule the owner happens to own: its persona is a platform hand-authored
        // template (MockDataInitializer#seedPersonaPrompt), so it must carry NO AI claim.
        jdbc.update("INSERT INTO tb_echo_capsule (owner_user_id, capsule_type, pseudonym, intro, "
                        + "persona_prompt, public_tags, authorized_memory_ids, visibility_status, is_public) "
                        + "VALUES (?, 'SEED_CAPSULE', 'cp31-seed-template', 'seed intro', "
                        + "'platform template persona, hand-authored', '[]', '[]', 'PRIVATE', false)",
                ownerId);

        MvcResult exported = mockMvc.perform(get("/api/me/data/export-package").session(owner))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode records = objectMapper.readTree(exported.getResponse().getContentAsString())
                .path("data").path("sections").path("echoCapsules").path("records");

        JsonNode userRecord = null;
        JsonNode seedRecord = null;
        for (JsonNode record : records) {
            if (record.path("importKey").asText().equals("echoCapsules:" + userCapsuleId)) {
                userRecord = record;
            }
            if ("cp31-seed-template".equals(record.path("pseudonym").asText())) {
                seedRecord = record;
            }
        }
        assertNotNull(userRecord, "the exported package must contain the user capsule record");
        assertNotNull(seedRecord, "the exported package must contain the seed capsule record");

        // User capsule: LLM persona → per-field AI claim, owner text never claimed as AI.
        assertTrue(userRecord.path("aiGenerated").asBoolean());
        assertEquals(List.of("personaPrompt"), toList(userRecord.path("aiGeneratedFields")));
        assertTrue(containsAll(toList(userRecord.path("ownerWrittenFields")),
                "pseudonym", "intro", "publicTags", "ownerContextNote"));
        assertFalse(toList(userRecord.path("aiGeneratedFields")).contains("intro"),
                "owner-written intro must not be labeled AI in the export");
        assertFalse(userRecord.path("personaPrompt").asText("").isBlank());

        // SEED capsule: platform template persona → no AI claim anywhere.
        assertFalse(seedRecord.path("aiGenerated").asBoolean(),
                "a SEED capsule persona is a hand-authored platform template, not LLM output");
        assertEquals(List.of(), toList(seedRecord.path("aiGeneratedFields")));

        // Same honesty rule on the owner detail endpoint for the SEED row.
        long seedCapsuleId = Long.parseLong(seedRecord.path("sourceRef").asText().split(":")[1]);
        mockMvc.perform(get("/api/capsule/" + seedCapsuleId).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiGenerated").value(false))
                .andExpect(jsonPath("$.data.aiGeneratedFields").value(empty()))
                .andExpect(jsonPath("$.data.ownerWrittenFields", hasItem("intro")));
    }

    // ---------- sanity: list endpoints keep their pre-existing payload fields ----------

    @Test
    void labelingIsAdditiveAndKeepsOriginalPayloadFields() throws Exception {
        long ownerId = seedUser("additive");
        MockHttpSession owner = login("additive", ownerId);
        long capsuleId = createPublicCapsule(owner, "cp31-增量字段");

        mockMvc.perform(get("/api/capsule/" + capsuleId).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pseudonym").value("cp31-增量字段"))
                .andExpect(jsonPath("$.data.personaPrompt").isNotEmpty())
                .andExpect(jsonPath("$.data.contextPreviewJson").exists())
                .andExpect(jsonPath("$.data.styleProfileJson").exists())
                .andExpect(jsonPath("$.data.ownerContextNote").value("owner 手写的语境备注。"))
                .andExpect(jsonPath("$.data.aiGenerated").value(true))
                .andExpect(jsonPath("$.data.aiGeneratedFields", hasItem("personaPrompt")))
                .andExpect(jsonPath("$.data.aiGeneratedFields", not(hasItems(
                        "intro", "ownerContextNote", "publicTags", "pseudonym",
                        "contextPreviewJson", "styleProfileJson"))));
    }
}
