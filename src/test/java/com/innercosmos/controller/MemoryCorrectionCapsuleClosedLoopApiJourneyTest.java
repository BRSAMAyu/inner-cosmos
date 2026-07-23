package com.innercosmos.controller;

import com.innercosmos.config.TestRateLimitConfig;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * G5.PROFILE-PROPAGATION + G6.SOCIAL-CLOSED-LOOP, proven via a REAL HTTP API journey (not unit
 * tests, not a browser) -- the handoff's item 3: Aurora/memory -> capsule -> matching/slow-social's
 * provenance/authorization/correction/withdrawal chain, driven end to end through the actual
 * controller/HTTP surface with two real registered accounts and real cross-request sessions.
 *
 * Existing evidence for this chain is either service-layer-direct (CapsuleGenomeServiceIntegrationTest,
 * UserCorrectionServiceImpl's own tests) or browser-driven (Playwright, evidence/integration/
 * FINAL-STABILIZATION-2026-07-20). This is the missing middle: a fast, CI-native Spring Boot
 * integration test that hits only real REST endpoints (register/login, capsule creation, persona
 * chat, correction confirm, capsule detail, data-rights receipts, correction retire) and asserts on
 * their real HTTP responses -- proving the chain does not secretly depend on frontend behavior.
 *
 * The only non-HTTP step is seeding the owner's initial MemoryCard via JdbcTemplate, matching the
 * existing convention (CapsuleSyncEndToEndIT, CapsuleP1P2PrivacyBoundaryTest) for a precondition
 * that itself belongs to a different, already-covered slice (Aurora memory extraction) --
 * everything from capsule creation onward is a real HTTP call.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "llm.mode=dev",
        "llm.provider=mock",
        "llm.allow-fallback=true",
        "spring.datasource.url=jdbc:h2:mem:testclosedloopapi;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always"
})
@AutoConfigureMockMvc
@Import(TestRateLimitConfig.class)
class MemoryCorrectionCapsuleClosedLoopApiJourneyTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("real API journey: memory correction propagates to capsule NEEDS_REVIEW, blocks the "
            + "visitor's next chat, and surfaces a data-rights receipt -- all through real HTTP")
    void correctionPropagatesThroughRealHttpAndBlocksTheVisitorsNextChat() throws Exception {
        // 1. Two real accounts, real register -> real session (HTTP), matching how a browser
        //    actually establishes identity -- not a seeded/synthetic session object.
        MockHttpSession ownerSession = register("owner");
        MockHttpSession visitorSession = register("visitor");
        Long ownerId = userId(ownerSession);

        // 2. Precondition seed: the owner already has one authored, ACTIVE memory (Aurora memory
        //    extraction itself is a separately-covered slice; this test starts from its output).
        Long memoryId = seedMemory(ownerId, "关于安全感的记忆", "user 在关系里需要先建立安全感才会敞开");

        // 3. Real HTTP: owner compiles a public capsule from that memory.
        String createBody = "{\"pseudonym\":\"回声\",\"intro\":\"一个真实的旅程测试\",\"memoryIds\":[" + memoryId + "]}";
        MvcResult createResult = mockMvc.perform(post("/api/capsule/create-from-memory")
                        .session(ownerSession).contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visibilityStatus").value("PUBLIC"))
                .andExpect(jsonPath("$.data.isPublic").value(true))
                .andReturn();
        Long capsuleId = extractLong(createResult, "$.data.id");

        // 4. Real HTTP: a genuinely different visitor account opens a persona-chat session and
        //    sends a message -- the capsule is reachable and answers while still authorized.
        String sessionBody = "{\"capsuleId\":" + capsuleId + "}";
        MvcResult sessionResult = mockMvc.perform(post("/api/persona-chat/session/create")
                        .session(visitorSession).contentType(MediaType.APPLICATION_JSON).content(sessionBody))
                .andExpect(status().isOk())
                .andReturn();
        Long personaSessionId = extractLong(sessionResult, "$.data.id");

        String messageBody = "{\"sessionId\":" + personaSessionId + ",\"message\":\"你好，可以聊聊吗？\"}";
        mockMvc.perform(post("/api/persona-chat/message")
                        .session(visitorSession).contentType(MediaType.APPLICATION_JSON).content(messageBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.textContent").isNotEmpty());

        // 5. Real HTTP: the owner authoritatively corrects the SAME memory the capsule authorized.
        //    This is the exact confirm() path that supersedes the memory, flags the
        //    AuthorizedMemoryRef NEEDS_REVIEW, de-lists the capsule, retires its matching vector,
        //    and records a retraction receipt (UserCorrectionServiceImpl#confirm).
        String correctionBody = "{\"targetType\":\"MEMORY_CARD\",\"targetId\":" + memoryId + ","
                + "\"newValue\":\"其实我在关系里更需要先看到对方的行动，而不是安全感的承诺\","
                + "\"reason\":\"real-api-journey-test\"}";
        mockMvc.perform(post("/api/aurora/corrections/confirm")
                        .session(ownerSession).contentType(MediaType.APPLICATION_JSON).content(correctionBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.propagation").isArray());

        // 6. Real HTTP, owner's own view: the capsule detail now shows NEEDS_REVIEW / not public --
        //    the correction genuinely propagated, verified through the same GET a real owner UI
        //    would call, not a direct DB peek.
        mockMvc.perform(get("/api/capsule/" + capsuleId).session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visibilityStatus").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.data.isPublic").value(false));

        // 7. Real HTTP, visitor's own next request: the SAME visitor session that chatted
        //    successfully in step 4 now gets rejected trying to continue in a NEW session against
        //    the withdrawn capsule -- the withdrawal is enforced live at the API boundary (a real
        //    403, via GlobalExceptionHandler's BusinessException->HttpStatus mapping), not just
        //    reflected in a status column nobody re-checks.
        mockMvc.perform(post("/api/persona-chat/session/create")
                        .session(visitorSession).contentType(MediaType.APPLICATION_JSON).content(sessionBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        // 8. Real HTTP: the owner's own data-rights receipts endpoint surfaces the auditable
        //    derivative-erasure receipt this correction produced -- the "complete receipts" half of
        //    G5.PROFILE-PROPAGATION's remaining note, proven end to end rather than seeded directly
        //    (contrast DataRightsControllerTest, which calls retractionReceiptService.record() itself).
        mockMvc.perform(get("/api/me/data-rights/receipts").session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.derivativeType=='CAPSULE_MATCH_INDEX')]").isNotEmpty())
                .andExpect(jsonPath("$.data[?(@.derivativeType=='MEMORY_EMBEDDING')]").isNotEmpty());

        // 9. Real HTTP: the correction itself is visible in the owner's own correction history and
        //    can be retired -- closing the loop with the same rollback surface UnderstandingCorrection.tsx
        //    exercises, but here proven at the API layer.
        MvcResult recentResult = mockMvc.perform(get("/api/aurora/corrections").session(ownerSession))
                .andExpect(status().isOk())
                .andReturn();
        Long correctionId = extractLong(recentResult, "$.data[0].id");

        mockMvc.perform(delete("/api/aurora/corrections/" + correctionId).session(ownerSession))
                .andExpect(status().isOk());

        MvcResult afterRetire = mockMvc.perform(get("/api/aurora/corrections").session(ownerSession))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(afterRetire.getResponse().getContentAsString().contains("\"data\":[]")
                        || !afterRetire.getResponse().getContentAsString().contains("\"id\":" + correctionId),
                "the retired correction must no longer appear in the owner's own correction list");
    }

    private MockHttpSession register(String prefix) throws Exception {
        String username = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        String json = "{\"username\":\"" + username + "\",\"password\":\"testPass123\","
                + "\"nickname\":\"" + prefix + " journey\"}";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Long userId(MockHttpSession session) {
        return (Long) session.getAttribute(com.innercosmos.common.Constants.SESSION_USER_KEY);
    }

    private Long seedMemory(Long ownerId, String title, String summary) {
        jdbc.update("""
                INSERT INTO tb_memory_card
                    (user_id, title, summary, status, version_no, consent_scope)
                VALUES (?, ?, ?, 'ACTIVE', 1, 'AURORA_PRIVATE')
                """, ownerId, title, summary);
        return jdbc.queryForObject(
                "SELECT id FROM tb_memory_card WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, ownerId);
    }

    private Long extractLong(MvcResult result, String jsonPath) throws Exception {
        com.jayway.jsonpath.DocumentContext context = com.jayway.jsonpath.JsonPath.parse(
                result.getResponse().getContentAsString());
        Object value = context.read(jsonPath);
        if (value instanceof Number number) return number.longValue();
        throw new AssertionError("expected a numeric value at " + jsonPath + " but got: " + value);
    }
}
