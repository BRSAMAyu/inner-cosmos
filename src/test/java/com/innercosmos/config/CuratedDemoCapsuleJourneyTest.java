package com.innercosmos.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
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
