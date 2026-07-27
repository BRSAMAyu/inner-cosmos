package com.innercosmos.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.AuroraSelfModel;
import com.innercosmos.entity.BeliefPattern;
import com.innercosmos.entity.DailyRecord;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.SlowLetter;
import com.innercosmos.entity.UserPortrait;
import com.innercosmos.mapper.AuroraSelfModelMapper;
import com.innercosmos.mapper.BeliefPatternMapper;
import com.innercosmos.mapper.DailyRecordMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.SlowLetterMapper;
import com.innercosmos.mapper.UserPortraitMapper;
import com.innercosmos.service.CapsuleGenomeService;
import com.innercosmos.service.DemoSandboxService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the classroom seed at its startup boundary in an isolated database. Other journey tests
 * intentionally finish Demo conversations and trigger capsule resynchronization, so sharing their
 * mutable context would test cross-test ordering instead of whether a fresh/restarted Demo is
 * immediately runnable.
 */
@SpringBootTest(properties = {
        "inner-cosmos.demo.seed-enabled=true",
        "spring.datasource.url=jdbc:h2:mem:curated-demo-capsules;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always",
        "llm.mode=dev",
        "llm.provider=mock",
        "llm.allow-fallback=true"
})
@AutoConfigureMockMvc
@Import(TestRateLimitConfig.class)
class CuratedDemoCapsuleJourneyTest {
    @Autowired MockMvc mockMvc;
    @Autowired CapsuleGenomeService capsuleGenomeService;
    @Autowired EchoCapsuleMapper echoCapsuleMapper;
    @Autowired DemoSandboxService demoSandboxService;
    @Autowired UserPortraitMapper userPortraitMapper;
    @Autowired AuroraSelfModelMapper auroraSelfModelMapper;
    @Autowired BeliefPatternMapper beliefPatternMapper;
    @Autowired MemoryCardMapper memoryCardMapper;
    @Autowired DailyRecordMapper dailyRecordMapper;
    @Autowired SlowLetterMapper slowLetterMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sameStoryCreatesSeparateOwnersAndStartsWithPrivateCapsules() {
        var first = demoSandboxService.createPersonalSandbox("lin-che");
        var second = demoSandboxService.createPersonalSandbox("lin-che");

        assertNotEquals(first.id, second.id);
        assertNotEquals(first.username, second.username);
        assertTrue(first.username.startsWith("sandbox-"));
        assertTrue(second.username.startsWith("sandbox-"));
        for (var owner : java.util.List.of(first, second)) {
            var capsules = echoCapsuleMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.innercosmos.entity.EchoCapsule>()
                            .eq("owner_user_id", owner.id));
            assertFalse(capsules.isEmpty());
            assertTrue(capsules.stream().allMatch(capsule ->
                    "PRIVATE".equals(capsule.visibilityStatus) && !Boolean.TRUE.equals(capsule.isPublic)));
        }
    }

    @Test
    void everyPersonalSandboxCarriesTheSameMatureSurfacesWithDifferentStoryEvidence() {
        java.util.Map<String, String> expectedLetterTitles = java.util.Map.of(
                "lin-che", "Turning a large vision into one small square of today",
                "shen-yan", "You didn't rush to choose one city, and I felt myself exhale",
                "xia-yu", "You said care should not have to prove itself through exhaustion");
        java.util.Set<String> portraitSignatures = new java.util.LinkedHashSet<>();
        java.util.Set<String> beliefSignatures = new java.util.LinkedHashSet<>();

        for (String key : expectedLetterTitles.keySet()) {
            var owner = demoSandboxService.createPersonalSandbox(key);

            var portraits = userPortraitMapper.selectList(new QueryWrapper<UserPortrait>()
                    .eq("user_id", owner.id));
            var selfModels = auroraSelfModelMapper.selectList(new QueryWrapper<AuroraSelfModel>()
                    .eq("user_id", owner.id).eq("status", "active"));
            var beliefs = beliefPatternMapper.selectList(new QueryWrapper<BeliefPattern>()
                    .eq("user_id", owner.id));
            var memories = memoryCardMapper.selectList(new QueryWrapper<MemoryCard>()
                    .eq("user_id", owner.id).eq("status", "ACTIVE"));
            var records = dailyRecordMapper.selectList(new QueryWrapper<DailyRecord>()
                    .eq("user_id", owner.id).eq("status", "ACTIVE"));
            var letters = slowLetterMapper.selectList(new QueryWrapper<SlowLetter>()
                    .eq("receiver_user_id", owner.id));

            assertTrue(portraits.size() >= 10, key + " must expose the complete portrait surface");
            assertTrue(selfModels.size() >= 4, key + " must expose Aurora relationship continuity");
            assertTrue(beliefs.size() >= 6, key + " must expose a lived-in belief gallery");
            assertTrue(memories.size() >= 5, key + " must expose a non-empty memory cosmos");
            assertTrue(records.size() >= 3, key + " must expose a multi-date lived timeline");
            assertTrue(letters.stream().anyMatch(letter ->
                            expectedLetterTitles.get(key).equals(letter.title)
                                    && "DELIVERED".equals(letter.status)
                                    && letter.receiverCapsuleId != null),
                    key + " must carry its own readable, capsule-grounded social clue");

            portraitSignatures.add(portraits.stream().map(row -> row.valueJson)
                    .sorted().reduce("", (left, right) -> left + right));
            beliefSignatures.add(beliefs.stream().map(row -> row.beliefContent)
                    .sorted().reduce("", (left, right) -> left + right));

            var capsules = echoCapsuleMapper.selectList(new QueryWrapper<com.innercosmos.entity.EchoCapsule>()
                    .eq("owner_user_id", owner.id));
            assertTrue(capsules.stream().allMatch(capsule ->
                    "PRIVATE".equals(capsule.visibilityStatus) && !Boolean.TRUE.equals(capsule.isPublic)));
        }

        assertTrue(portraitSignatures.size() == 3,
                "the three mature stories must not share one generic portrait");
        assertTrue(beliefSignatures.size() == 3,
                "the three mature stories must not share one generic belief set");
    }

    @Test
    void everyCuratedMirrorHasGrantsGenomeIrAndCanStartAConversation() throws Exception {
        MockHttpSession visitor = loginAsAdmin();
        MvcResult plazaResult = mockMvc.perform(get("/api/plaza/capsules").session(visitor))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode capsules = objectMapper.readTree(plazaResult.getResponse().getContentAsString()).path("data");

        for (String pseudonym : new String[]{
                "Lin Che's Echo",
                "The One Who Walks by the River",
                "The One Learning to Include Herself in Care"
        }) {
            JsonNode selected = null;
            for (JsonNode capsule : capsules) {
                if (pseudonym.equals(capsule.path("pseudonym").asText())) {
                    if (selected != null) throw new AssertionError("Ambiguous curated mirror: " + pseudonym);
                    selected = capsule;
                }
            }
            if (selected == null) throw new AssertionError("Missing curated mirror: " + pseudonym);

            long capsuleId = selected.path("id").asLong();
            assertNotNull(capsuleGenomeService.current(capsuleId),
                    "Curated mirror must have an active Genome: " + pseudonym);
            assertTrue(echoCapsuleMapper.selectById(capsuleId).contextPreviewJson.contains("\"genomeIr\""),
                    "Curated mirror must expose provenance-carrying Genome IR: " + pseudonym);
            mockMvc.perform(post("/api/v1/persona-chat/session/create")
                            .session(visitor)
                            .header("Idempotency-Key", "curated-demo-" + capsuleId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"capsuleId\":" + capsuleId + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.data.capsuleId").value(capsuleId));
        }
    }

    @Test
    void allOfficialSeedsAppearPubliclyAndOneCompletesAThreeTurnDemoTrajectory() throws Exception {
        MockHttpSession visitor = loginAsAdmin();
        JsonNode capsules = objectMapper.readTree(mockMvc.perform(get("/api/plaza/capsules").session(visitor))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .path("data");
        java.util.Map<String, Long> visible = new java.util.LinkedHashMap<>();
        for (JsonNode capsule : capsules) {
            String name = capsule.path("pseudonym").asText();
            if (SeedCapsuleContent.seeds().stream().anyMatch(seed -> seed.name().equals(name))) {
                visible.put(name, capsule.path("id").asLong());
            }
        }
        assertTrue(visible.size() == 10, "the plaza must expose all ten official seed capsules: " + visible.keySet());
        for (var seed : SeedCapsuleContent.seeds()) {
            var entity = echoCapsuleMapper.selectById(visible.get(seed.name()));
            assertTrue(Boolean.TRUE.equals(entity.isPublic));
            assertTrue("PUBLIC".equals(entity.visibilityStatus));
            assertTrue("SEED_CAPSULE".equals(entity.capsuleType));
        }

        long luoId = visible.get("Luo");
        MvcResult created = mockMvc.perform(post("/api/v1/persona-chat/session/create")
                        .session(visitor)
                        .header("Idempotency-Key", "official-seed-trajectory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capsuleId\":" + luoId + "}"))
                .andExpect(status().isOk()).andReturn();
        long sessionId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        java.util.Set<String> replies = new java.util.LinkedHashSet<>();
        int turn = 0;
        for (String message : java.util.List.of("论文完全写不动了", "还是觉得第一步很重", "我已经写下标题了")) {
            MvcResult response = mockMvc.perform(post("/api/v1/persona-chat/message")
                            .session(visitor)
                            .header("Idempotency-Key", "official-seed-turn-" + (++turn))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(java.util.Map.of(
                                    "sessionId", sessionId, "message", message))))
                    .andExpect(status().isOk()).andReturn();
            replies.add(objectMapper.readTree(response.getResponse().getContentAsString())
                    .path("data").path("textContent").asText());
        }
        assertTrue(replies.size() == 3, "three real service turns must not repeat: " + replies);
        assertTrue(replies.stream().noneMatch(reply -> reply.contains("有限的数字回声")));
    }

    @Test
    void everyCuratedPersonaStartsWithAnArrivedStorySpecificSlowLetter() throws Exception {
        assertInboxContains("demo", "Turning a large vision into one small square of today");
        assertInboxContains("river", "You didn't rush to choose one city, and I felt myself exhale");
        assertInboxContains("cloud", "You said care should not have to prove itself through exhaustion");
    }

    private void assertInboxContains(String username, String title) throws Exception {
        MockHttpSession session = login(username, "demo123");
        MvcResult result = mockMvc.perform(get("/api/letters/inbox").session(session))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode letters = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        for (JsonNode letter : letters) {
            if (title.equals(letter.path("title").asText())) {
                assertTrue(letter.path("receiverCapsuleId").asLong() > 0,
                        "Curated slow letters must point to a real receiver capsule");
                assertTrue("DELIVERED".equals(letter.path("status").asText())
                                || "READ".equals(letter.path("status").asText()),
                        "Curated inbox letter must already be readable");
                return;
            }
        }
        throw new AssertionError("Missing curated slow letter for " + username + ": " + title);
    }

    private MockHttpSession loginAsAdmin() throws Exception {
        return login("admin", "admin123");
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }
}
