package com.innercosmos.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.config.TestRateLimitConfig;
import com.innercosmos.common.Constants;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.AuthorizedMemoryRefMapper;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.AuthorizedMemoryRef;
import com.innercosmos.service.MemoryService;
import org.junit.jupiter.api.BeforeEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RUN-005 integration coverage for the Aurora correction feedback loop:
 * - auth required
 * - POST /api/aurora/corrections persists a free-form self-understanding correction
 *   end-to-end (exercises the tb_user_correction NOT NULL columns via the controller
 *   defaults), and GET returns it newest-first
 * - a blank newValue is rejected (BAD_REQUEST), never stored.
 */
@SpringBootTest(properties = {
        "llm.mode=dev",
        "llm.provider=minimax",
        "llm.api-key=",
        "llm.minimax.api-key=",
        "llm.allow-fallback=true",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.datasource.url=jdbc:h2:mem:testcorrection;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always"
})
@AutoConfigureMockMvc
@Import(TestRateLimitConfig.class)
class UserCorrectionControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MemoryCardMapper memoryCardMapper;

    @Autowired
    MemoryService memoryService;

    @Autowired EchoCapsuleMapper capsuleMapper;
    @Autowired AuthorizedMemoryRefMapper authorizedMemoryRefMapper;

    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        session = loginWithUniqueUser();
    }

    @Test
    void corrections_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/aurora/corrections"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void record_thenList_persistsNewestFirst() throws Exception {
        postCorrection("我换工作是因为想成长，不是逃避", "你以为我在逃避", "请别再这样理解我");
        postCorrection("我喜欢独处但并不孤僻", null, null);

        mockMvc.perform(get("/api/aurora/corrections").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].newValue").value("我喜欢独处但并不孤僻"))
                .andExpect(jsonPath("$.data[0].fieldName").value("self_understanding"))
                .andExpect(jsonPath("$.data[0].targetType").value("AURORA_UNDERSTANDING"))
                .andExpect(jsonPath("$.data[1].newValue").value("我换工作是因为想成长，不是逃避"))
                .andExpect(jsonPath("$.data[1].oldValue").value("你以为我在逃避"));
    }

    @Test
    void record_withPortraitDimTargetType_persistsThatType() throws Exception {
        mockMvc.perform(post("/api/aurora/corrections")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newValue\":\"我其实更喜欢独处\",\"oldValue\":\"你以为我很外向\","
                                + "\"targetType\":\"PORTRAIT_DIM\",\"fieldName\":\"SOCIAL_STYLE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.targetType").value("PORTRAIT_DIM"))
                .andExpect(jsonPath("$.data.fieldName").value("SOCIAL_STYLE"));

        mockMvc.perform(get("/api/aurora/corrections").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].newValue").value("我其实更喜欢独处"))
                .andExpect(jsonPath("$.data[0].targetType").value("PORTRAIT_DIM"));
    }

    @Test
    void record_blankNewValue_isRejected() throws Exception {
        mockMvc.perform(post("/api/aurora/corrections")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newValue\":\"   \"}"))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));

        mockMvc.perform(get("/api/aurora/corrections").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void preview_isReadOnly_thenConfirmCreatesVersionedClaimAndPropagation() throws Exception {
        String body = "{\"targetType\":\"AURORA_UNDERSTANDING\",\"targetId\":0,"
                + "\"fieldName\":\"self_understanding\",\"oldValue\":\"我总是在逃避\","
                + "\"newValue\":\"我是在谨慎选择下一步\",\"reason\":\"这是我的明确纠正\"}";

        mockMvc.perform(post("/api/aurora/corrections/preview").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmationRequired").value(true))
                .andExpect(jsonPath("$.data.impacts[0].kind").value("AURORA_RETRIEVAL"));
        mockMvc.perform(get("/api/aurora/corrections").session(session))
                .andExpect(jsonPath("$.data.length()").value(0));

        MvcResult result = mockMvc.perform(post("/api/aurora/corrections/confirm").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correction.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.activeClaim.authorityLevel").value("USER_CORRECTION"))
                .andExpect(jsonPath("$.data.activeClaim.version").value(1))
                .andExpect(jsonPath("$.data.propagation[0].status").value("APPLIED"))
                .andReturn();
        long correctionId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("correction").path("id").asLong();

        mockMvc.perform(get("/api/aurora/corrections/claims").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
        mockMvc.perform(get("/api/aurora/corrections/" + correctionId + "/propagation").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].targetKind").value("AURORA_RETRIEVAL"));
    }

    @Test
    void repeatedConfirmationSupersedesOldClaimWithoutDeletingHistory() throws Exception {
        String first = "{\"newValue\":\"我需要独处恢复\"}";
        String second = "{\"newValue\":\"我需要独处恢复，但也珍惜稳定陪伴\"}";
        mockMvc.perform(post("/api/aurora/corrections/confirm").session(session)
                .contentType(MediaType.APPLICATION_JSON).content(first)).andExpect(status().isOk());
        mockMvc.perform(post("/api/aurora/corrections/confirm").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(second))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.activeClaim.version").value(2));
        mockMvc.perform(get("/api/aurora/corrections/claims").session(session))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[1].status").value("SUPERSEDED"));
    }

    @Test
    void targetedMemoryCorrectionSupersedesCardAndRemovesItFromCurrentStarfield() throws Exception {
        Long userId = (Long) session.getAttribute(Constants.SESSION_USER_KEY);
        MemoryCard card = new MemoryCard();
        card.userId = userId;
        card.title = "我总是在逃避";
        card.summary = "旧的单一判断";
        card.status = "ACTIVE";
        card.emotionalGravity = 2.0;
        memoryCardMapper.insert(card);
        org.junit.jupiter.api.Assertions.assertTrue(memoryService.starfield(userId).stream()
                .anyMatch(star -> card.id.equals(star.id)));

        String body = "{\"targetType\":\"MEMORY_CARD\",\"targetId\":" + card.id
                + ",\"fieldName\":\"summary\",\"oldValue\":\"我总是在逃避\","
                + "\"newValue\":\"我是在谨慎选择下一步\"}";
        mockMvc.perform(post("/api/aurora/corrections/preview").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.affectedMemoryCount").value(1))
                .andExpect(jsonPath("$.data.impacts[?(@.kind == 'STARFIELD')]").exists());
        mockMvc.perform(post("/api/aurora/corrections/confirm").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals("SUPERSEDED", memoryCardMapper.selectById(card.id).status);
        org.junit.jupiter.api.Assertions.assertTrue(memoryService.starfield(userId).stream()
                .noneMatch(star -> card.id.equals(star.id)));
    }

    @Test
    void correctionImmediatelyUnpublishesAffectedCapsuleAndRequiresAuthorizationReview() throws Exception {
        Long userId = (Long) session.getAttribute(Constants.SESSION_USER_KEY);
        MemoryCard card = new MemoryCard();
        card.userId = userId; card.title = "旧的关系判断"; card.summary = "我不需要任何陪伴";
        card.status = "ACTIVE"; memoryCardMapper.insert(card);
        EchoCapsule capsule = new EchoCapsule();
        capsule.ownerUserId = userId; capsule.capsuleType = "USER_CAPSULE"; capsule.pseudonym = "待复核回声";
        capsule.visibilityStatus = "PUBLIC"; capsule.isPublic = true;
        capsule.authorizedMemoryIds = "[\"" + card.id + "\"]"; capsuleMapper.insert(capsule);
        AuthorizedMemoryRef ref = new AuthorizedMemoryRef();
        ref.capsuleId = capsule.id; ref.memoryCardId = card.id;
        ref.authorizationStatus = "AUTHORIZED"; ref.abstractExcerpt = card.summary;
        authorizedMemoryRefMapper.insert(ref);

        String body = "{\"targetType\":\"MEMORY_CARD\",\"targetId\":" + card.id
                + ",\"fieldName\":\"summary\",\"oldValue\":\"我不需要任何陪伴\","
                + "\"newValue\":\"我珍惜稳定但低压力的陪伴\"}";
        mockMvc.perform(post("/api/aurora/corrections/confirm").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        EchoCapsule changed = capsuleMapper.selectById(capsule.id);
        org.junit.jupiter.api.Assertions.assertFalse(changed.isPublic);
        org.junit.jupiter.api.Assertions.assertEquals("NEEDS_REVIEW", changed.visibilityStatus);
        org.junit.jupiter.api.Assertions.assertEquals("NEEDS_REVIEW",
                authorizedMemoryRefMapper.selectById(ref.id).authorizationStatus);
    }

    @Test
    void retiringLatestCorrectionRestoresPreviousClaimAndKeepsAuditHistory() throws Exception {
        MvcResult first = mockMvc.perform(post("/api/aurora/corrections/confirm").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newValue\":\"第一版理解\"}"))
                .andExpect(status().isOk()).andReturn();
        long firstId = objectMapper.readTree(first.getResponse().getContentAsString())
                .path("data").path("activeClaim").path("id").asLong();
        MvcResult second = mockMvc.perform(post("/api/aurora/corrections/confirm").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newValue\":\"第二版理解\"}"))
                .andExpect(status().isOk()).andReturn();
        long correctionId = objectMapper.readTree(second.getResponse().getContentAsString())
                .path("data").path("correction").path("id").asLong();

        mockMvc.perform(delete("/api/aurora/corrections/" + correctionId).session(session))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/aurora/corrections/claims").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + firstId + ")].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[?(@.correctionId == " + correctionId + ")].status").value("RETIRED"));
        mockMvc.perform(get("/api/aurora/corrections/" + correctionId + "/propagation").session(session))
                .andExpect(jsonPath("$.data[?(@.status == 'WITHDRAWN')]").exists())
                .andExpect(jsonPath("$.data[?(@.status == 'REVIEW_REQUIRED')]").exists());
    }

    // ---------------- helpers ----------------

    private void postCorrection(String newValue, String oldValue, String reason) throws Exception {
        StringBuilder json = new StringBuilder("{\"newValue\":").append(quote(newValue));
        if (oldValue != null) json.append(",\"oldValue\":").append(quote(oldValue));
        if (reason != null) json.append(",\"reason\":").append(quote(reason));
        json.append("}");
        mockMvc.perform(post("/api/aurora/corrections")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber());
    }

    private static String quote(String s) {
        return "\"" + s.replace("\"", "\\\"") + "\"";
    }

    private MockHttpSession loginWithUniqueUser() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "corruser_" + suffix;
        String password = "testPass123";

        String registerJson = "{\"username\":\"" + username + "\","
                + "\"password\":\"" + password + "\","
                + "\"nickname\":\"Correction Test\"}";

        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession s = (MockHttpSession) regResult.getRequest().getSession(false);
        if (s == null) {
            String loginJson = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson))
                    .andExpect(status().isOk())
                    .andReturn();
            s = (MockHttpSession) loginResult.getRequest().getSession(false);
        }
        return s;
    }
}
