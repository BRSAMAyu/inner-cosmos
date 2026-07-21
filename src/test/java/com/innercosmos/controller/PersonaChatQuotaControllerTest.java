package com.innercosmos.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IC-CAP-001: integration coverage for {@code GET /api/persona-chat/quota}.
 *
 * The endpoint is the authoritative per-day quota read path: turnCount comes
 * from tb_capsule_usage_quota (0 when no row exists today), dailyLimit from
 * resolveDailyLimit (SEED=50, otherwise clamped configured value), remaining
 * = max(0, dailyLimit - turnCount), seed = whether the capsule is a SEED type.
 */
@SpringBootTest(properties = {
        "llm.mode=dev",
        "llm.provider=mock",
        "llm.allow-fallback=true",
        "spring.main.allow-bean-definition-overriding=true"
})
@AutoConfigureMockMvc
@Import(com.innercosmos.config.TestRateLimitConfig.class)
class PersonaChatQuotaControllerTest {

    // Must match PersonaChatServiceImpl.QUOTA_ZONE so the seeded quota_date agrees with the
    // date the product computes when reading the quota.
    private static final ZoneId QUOTA_ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        session = loginWithUniqueUser();
    }

    @Test
    @DisplayName("quota requires auth (401 when anonymous)")
    void quota_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/persona-chat/quota").param("capsuleId", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("quota with no usage today returns turnCount=0, remaining=dailyLimit")
    void quota_noUsageToday_returnsZero() throws Exception {
        long userId = currentUserId();
        long capsuleId = seedUserCapsule(userId, 30);

        mockMvc.perform(get("/api/persona-chat/quota").param("capsuleId", String.valueOf(capsuleId)).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.turnCount").value(0))
                .andExpect(jsonPath("$.data.dailyLimit").value(30))
                .andExpect(jsonPath("$.data.remaining").value(30))
                .andExpect(jsonPath("$.data.seed").value(false))
                .andExpect(jsonPath("$.data.quotaDate").isString());
    }

    @Test
    @DisplayName("quota with partial usage computes remaining = dailyLimit - turnCount")
    void quota_partialUsage_computesRemaining() throws Exception {
        long userId = currentUserId();
        long capsuleId = seedUserCapsule(userId, 10);
        // Pre-fill 4 turns today. Key the quota row by the SAME zone the product uses
        // (PersonaChatServiceImpl.QUOTA_ZONE = Asia/Shanghai), not SQL CURRENT_DATE which is the
        // DB/JVM zone (UTC on CI) -- otherwise the row's date and the controller's lookup date
        // disagree in the 16:00-24:00 UTC window and the quota reads back as 0.
        jdbc.update(
                "INSERT INTO tb_capsule_usage_quota (visitor_user_id, capsule_id, quota_date, turn_count) "
                        + "VALUES (?, ?, ?, 4)",
                userId, capsuleId, LocalDate.now(QUOTA_ZONE));

        mockMvc.perform(get("/api/persona-chat/quota").param("capsuleId", String.valueOf(capsuleId)).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.turnCount").value(4))
                .andExpect(jsonPath("$.data.dailyLimit").value(10))
                .andExpect(jsonPath("$.data.remaining").value(6))
                .andExpect(jsonPath("$.data.seed").value(false));
    }

    @Test
    @DisplayName("quota for SEED capsule reports seed=true and dailyLimit=50")
    void quota_seedCapsule_reportsSeedAndEffectiveLimit() throws Exception {
        long userId = currentUserId();
        // Insert a SEED capsule (no explicit conversationLimitPerDay)
        jdbc.update(
                "INSERT INTO tb_echo_capsule (owner_user_id, capsule_type, pseudonym, intro, "
                        + "visibility_status, is_public) VALUES (?, ?, ?, ?, ?, ?)",
                userId, "SEED_CAPSULE", "seed-echo", "seed intro", "PUBLIC", true);
        Long capsuleId = jdbc.queryForObject(
                "SELECT id FROM tb_echo_capsule WHERE owner_user_id = ? AND capsule_type = 'SEED_CAPSULE' ORDER BY id DESC LIMIT 1",
                Long.class, userId);

        mockMvc.perform(get("/api/persona-chat/quota").param("capsuleId", String.valueOf(capsuleId)).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dailyLimit").value(50))
                .andExpect(jsonPath("$.data.seed").value(true))
                .andExpect(jsonPath("$.data.remaining").value(50));
    }

    @Test
    @DisplayName("quota clamps turnCount at dailyLimit so remaining never goes negative")
    void quota_atLimit_remainingIsZero() throws Exception {
        long userId = currentUserId();
        long capsuleId = seedUserCapsule(userId, 5);
        // Pre-fill MORE than the limit (simulates a previous higher limit or manual edit)
        jdbc.update(
                "INSERT INTO tb_capsule_usage_quota (visitor_user_id, capsule_id, quota_date, turn_count) "
                        + "VALUES (?, ?, ?, 99)",
                userId, capsuleId, LocalDate.now(QUOTA_ZONE));

        mockMvc.perform(get("/api/persona-chat/quota").param("capsuleId", String.valueOf(capsuleId)).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.turnCount").value(99))
                .andExpect(jsonPath("$.data.remaining").value(0));
    }

    // ---------------- helpers ----------------

    private long currentUserId() {
        Object value = session.getAttribute("LOGIN_USER_ID");
        return ((Number) value).longValue();
    }

    /** Insert a public USER_CAPSULE owned by userId with the given daily limit. */
    private long seedUserCapsule(long userId, int dailyLimit) {
        jdbc.update(
                "INSERT INTO tb_echo_capsule (owner_user_id, capsule_type, pseudonym, intro, "
                        + "visibility_status, is_public, conversation_limit_per_day) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                userId, "USER_CAPSULE", "test-echo", "intro", "PUBLIC", true, dailyLimit);
        Long id = jdbc.queryForObject(
                "SELECT id FROM tb_echo_capsule WHERE owner_user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class, userId);
        return id;
    }

    private MockHttpSession loginWithUniqueUser() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "quotauser_" + suffix;
        String password = "testPass123";

        String registerJson = "{\"username\":\"" + username + "\","
                + "\"password\":\"" + password + "\","
                + "\"nickname\":\"Quota Test\"}";

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
