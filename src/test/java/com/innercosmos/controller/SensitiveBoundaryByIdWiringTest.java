package com.innercosmos.controller;

import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.TodoItem;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.TodoItemMapper;
import com.innercosmos.service.privacy.RetractionTombstoneService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CP-14 §2-2: the by-id wiring matrix. Every consumer endpoint that reads a P1/P2 asset by id
 * now routes through {@link com.innercosmos.service.privacy.SensitiveDataBoundaryService}; this
 * class proves the cross-user negative per endpoint (user B touching user A's asset is refused
 * UNAUTHORIZED/FORBIDDEN — never the content), plus the CAPSULE_RUNTIME visitor semantics and
 * the CP-15 anti-resurrection behavior observed through the wired endpoints themselves.
 */
@SpringBootTest(properties = {
        "llm.mode=dev",
        "llm.provider=mock",
        "llm.allow-fallback=true",
        "spring.main.allow-bean-definition-overriding=true"
})
@AutoConfigureMockMvc
@Import(com.innercosmos.config.TestRateLimitConfig.class)
class SensitiveBoundaryByIdWiringTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    MemoryCardMapper memoryCardMapper;
    @Autowired
    TodoItemMapper todoItemMapper;
    @Autowired
    EchoCapsuleMapper capsuleMapper;
    @Autowired
    RetractionTombstoneService tombstoneService;

    // ---------------- seeding ----------------

    private MockHttpSession loginUniqueUser() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "cp14wire_" + suffix;
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\","
                                + "\"password\":\"testPass123\",\"nickname\":\"矩阵测试\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        if (session == null || session.getAttribute("LOGIN_USER_ID") == null) {
            MvcResult login = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + username + "\",\"password\":\"testPass123\"}"))
                    .andExpect(status().isOk())
                    .andReturn();
            session = (MockHttpSession) login.getRequest().getSession(false);
        }
        return session;
    }

    private long userIdOf(MockHttpSession session) {
        return ((Number) session.getAttribute("LOGIN_USER_ID")).longValue();
    }

    private MemoryCard memoryCard(long ownerId) {
        MemoryCard card = new MemoryCard();
        card.userId = ownerId;
        card.title = "CP-14 矩阵记忆";
        card.summary = "用于越权矩阵测试";
        card.memoryType = "FACT";
        card.status = "ACTIVE";
        card.visibilityLevel = "PRIVATE";
        card.emotionalGravity = 0.5;
        memoryCardMapper.insert(card);
        return card;
    }

    private TodoItem todo(long ownerId) {
        TodoItem item = new TodoItem();
        item.userId = ownerId;
        item.taskName = "矩阵待办";
        item.status = "TODO";
        item.priority = "MEDIUM";
        todoItemMapper.insert(item);
        return item;
    }

    private EchoCapsule capsule(long ownerId, boolean publicListed) {
        EchoCapsule capsule = new EchoCapsule();
        capsule.ownerUserId = ownerId;
        capsule.pseudonym = "矩阵共鸣体-" + UUID.randomUUID().toString().substring(0, 8);
        capsule.intro = "越权矩阵测试";
        capsule.visibilityStatus = publicListed ? "PUBLIC" : "PRIVATE";
        capsule.isPublic = publicListed;
        capsuleMapper.insert(capsule);
        return capsule;
    }

    // ---------------- P1: memory card endpoints ----------------

    @Test
    void memoryCardEndpointsRefuseStranger() throws Exception {
        MockHttpSession owner = loginUniqueUser();
        MockHttpSession stranger = loginUniqueUser();
        MemoryCard card = memoryCard(userIdOf(owner));

        mockMvc.perform(get("/api/memory/starfield/{id}/detail", card.id).session(stranger))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/memory/cards/{id}/importance", card.id).session(stranger)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"importance\":0.9}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/memory/cards/{id}/archive", card.id).session(stranger))
                .andExpect(status().isUnauthorized());
        // Positive control: the owner still reads their own card through the same wired endpoint.
        mockMvc.perform(get("/api/memory/starfield/{id}/detail", card.id).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shredderCardEndpointsRefuseStranger() throws Exception {
        MockHttpSession owner = loginUniqueUser();
        MockHttpSession stranger = loginUniqueUser();
        MemoryCard card = memoryCard(userIdOf(owner));
        card.memoryType = "SHREDDER";
        memoryCardMapper.updateById(card);

        mockMvc.perform(post("/api/thought-shredder/{id}/settle", card.id).session(stranger))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/thought-shredder/{id}", card.id).session(stranger))
                .andExpect(status().isUnauthorized());
    }

    // ---------------- P1: todo endpoints ----------------

    @Test
    void todoEndpointsRefuseStranger() throws Exception {
        MockHttpSession owner = loginUniqueUser();
        MockHttpSession stranger = loginUniqueUser();
        TodoItem item = todo(userIdOf(owner));

        mockMvc.perform(post("/api/todos/{id}/status", item.id).session(stranger)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/todos/{id}", item.id).session(stranger)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"taskName\":\"抢注\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/todos/{id}", item.id).session(stranger))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/todos/{id}/split", item.id).session(stranger))
                .andExpect(status().isUnauthorized());
        // Positive control: the owner's own status update flows through the same guard.
        mockMvc.perform(post("/api/todos/{id}/status", item.id).session(owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ---------------- P2: owner-facing capsule endpoints ----------------

    @Test
    void capsuleOwnerEndpointsRefuseStranger() throws Exception {
        MockHttpSession owner = loginUniqueUser();
        MockHttpSession stranger = loginUniqueUser();
        EchoCapsule capsule = capsule(userIdOf(owner), false);

        mockMvc.perform(get("/api/capsule/{id}/genome-history", capsule.id).session(stranger))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/capsule/{id}/data-use-grants", capsule.id).session(stranger))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/capsule/{id}/data-use-grants/{grantId}/revoke", capsule.id, 1L)
                        .session(stranger)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"probe\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/capsule/{id}/sandbox/respond", capsule.id).session(stranger)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"你是谁\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/capsule/{id}/sandbox/feedback", capsule.id).session(stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"genomeVersionId\":1,\"question\":\"q\",\"response\":\"r\",\"rating\":\"GOOD\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/capsule/{id}/sandbox/feedback", capsule.id).session(stranger))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/capsule/{id}/sandbox/fidelity", capsule.id).session(stranger))
                .andExpect(status().isUnauthorized());
        // Positive control: the owner reads their own capsule's wired endpoints.
        mockMvc.perform(get("/api/capsule/{id}/genome-history", capsule.id).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get("/api/capsule/{id}/sandbox/fidelity", capsule.id).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ---------------- P2: CAPSULE_RUNTIME visitor paths ----------------

    @Test
    void capsuleRuntimeAllowsPublishedCapsuleButRefusesPrivateAndMissing() throws Exception {
        MockHttpSession owner = loginUniqueUser();
        MockHttpSession visitor = loginUniqueUser();
        EchoCapsule published = capsule(userIdOf(owner), true);
        EchoCapsule privateCapsule = capsule(userIdOf(owner), false);

        // A published capsule is visitor-readable for the runtime purpose.
        mockMvc.perform(get("/api/persona-chat/capsule/{id}/active-session", published.id).session(visitor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get("/api/persona-chat/quota").param("capsuleId", String.valueOf(published.id))
                        .session(visitor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // A private capsule is FORBIDDEN for a stranger — never its quota config.
        mockMvc.perform(get("/api/persona-chat/capsule/{id}/active-session", privateCapsule.id).session(visitor))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/persona-chat/quota").param("capsuleId", String.valueOf(privateCapsule.id))
                        .session(visitor))
                .andExpect(status().isForbidden());

        // A nonexistent capsule reads as absent — no existence hint.
        mockMvc.perform(get("/api/persona-chat/quota").param("capsuleId", "999999999").session(visitor))
                .andExpect(status().isNotFound());
    }

    @Test
    void capsuleRuntimeOwnerStillReadsOwnPrivateCapsuleQuota() throws Exception {
        MockHttpSession owner = loginUniqueUser();
        EchoCapsule capsule = capsule(userIdOf(owner), false);

        // CAPSULE_RUNTIME keeps the owner's own path open even for a private capsule.
        mockMvc.perform(get("/api/persona-chat/quota").param("capsuleId", String.valueOf(capsule.id))
                        .session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ---------------- CP-15 anti-resurrection through the wired endpoints ----------------

    @Test
    void tombstonedCapsuleStaysUnreachableForRuntimeVisitor() throws Exception {
        MockHttpSession owner = loginUniqueUser();
        MockHttpSession visitor = loginUniqueUser();
        EchoCapsule published = capsule(userIdOf(owner), true);

        tombstoneService.record("CAPSULE", published.id, userIdOf(owner), null, "owner withdrawal");

        mockMvc.perform(get("/api/persona-chat/capsule/{id}/active-session", published.id).session(visitor))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/persona-chat/quota").param("capsuleId", String.valueOf(published.id))
                        .session(visitor))
                .andExpect(status().isNotFound());
    }

    @Test
    void tombstonedMemoryCardBlocksEvenTheOwnerAtWiredEndpoint() throws Exception {
        MockHttpSession owner = loginUniqueUser();
        MemoryCard card = memoryCard(userIdOf(owner));

        tombstoneService.record("MEMORY", card.id, userIdOf(owner), null, "owner forget");

        mockMvc.perform(get("/api/memory/starfield/{id}/detail", card.id).session(owner))
                .andExpect(status().isNotFound());
    }
}
