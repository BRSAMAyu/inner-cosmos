package com.innercosmos.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.config.TestRateLimitConfig;
import com.innercosmos.dto.SafetyCheckRequest;
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

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "llm.mode=dev",
        "llm.provider=minimax",
        "llm.api-key=",
        "llm.minimax.api-key=",
        "llm.allow-fallback=true",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.datasource.url=jdbc:h2:mem:testsafety;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always"
})
@AutoConfigureMockMvc
@Import(TestRateLimitConfig.class)
class SafetyControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        session = loginWithUniqueUser();
    }

    // ---------------- Resources ----------------

    @Test
    void resources_returnsNonEmptyList() throws Exception {
        mockMvc.perform(get("/api/safety/resources").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)));
    }

    @Test
    void resources_isAccessibleWithoutAuth() throws Exception {
        // Crisis resources remain public so a signed-out user can still reach help.
        mockMvc.perform(get("/api/safety/resources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // ---------------- Check ----------------

    @Test
    void check_withSafeText_returnsTrue() throws Exception {
        SafetyCheckRequest request = new SafetyCheckRequest();
        request.text = "今天天气很好，心情也不错。";

        mockMvc.perform(post("/api/safety/check")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void check_withNullText_handled() throws Exception {
        // Build JSON with explicit null text to ensure deserialization succeeds
        String body = "{\"sessionId\":null,\"text\":null}";

        mockMvc.perform(post("/api/safety/check")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    // Null text may be accepted (returning true) or rejected (400)
                    if (status != 200 && status != 400) {
                        throw new AssertionError("Expected 200 or 400 but got " + status);
                    }
                });
    }

    // ---------------- Inspect ----------------

    @Test
    void inspect_returnsResult() throws Exception {
        SafetyCheckRequest request = new SafetyCheckRequest();
        request.text = "我想了解如何更好地管理时间。";

        mockMvc.perform(post("/api/safety/inspect")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists());
    }

    // ---------------- VS-002: implicit-crisis + casual-venting via full Spring context ----------------

    @Test
    void check_withExplicitCrisisKeyword_isBlocked() throws Exception {
        // Regression guard: explicit crisis keyword still blocks synchronously.
        SafetyCheckRequest request = new SafetyCheckRequest();
        request.text = "我想要自杀"; // explicit crisis keyword

        mockMvc.perform(post("/api/safety/check")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(result -> {
                    // Blocked path throws SafetyBlockedException → non-200.
                    int status = result.getResponse().getStatus();
                    if (status == 200) {
                        throw new AssertionError("Explicit crisis keyword should be blocked, but got 200");
                    }
                });
    }

    @Test
    void check_withImplicitCrisisDistress_isBlocked() throws Exception {
        // Distress signal + genuine crisis intent (conservative fallback in Mock mode → HIGH).
        SafetyCheckRequest request = new SafetyCheckRequest();
        request.text = "我真的是大家的累赘，想要彻底解脱，告别这个世界";

        mockMvc.perform(post("/api/safety/check")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 200) {
                        throw new AssertionError("Implicit genuine-crisis distress should be blocked, but got 200");
                    }
                });
    }

    @Test
    void check_withCasualVenting_isAllowed() throws Exception {
        // False-positive guard: tired/overworked hyperbole must NOT be blocked or medicalized.
        SafetyCheckRequest request = new SafetyCheckRequest();
        request.text = "今天加班累死了，困得要死，好想睡觉";

        mockMvc.perform(post("/api/safety/check")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void inspect_implicitCrisisDistress_returnsHighBlock() throws Exception {
        SafetyCheckRequest request = new SafetyCheckRequest();
        request.text = "我真的是大家的累赘，想要彻底解脱，告别这个世界";

        mockMvc.perform(post("/api/safety/inspect")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.data.blockModelCall").value(true));
    }

    @Test
    void inspect_casualVenting_returnsLowAllow() throws Exception {
        SafetyCheckRequest request = new SafetyCheckRequest();
        request.text = "今天加班累死了，困得要死";

        mockMvc.perform(post("/api/safety/inspect")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.blockModelCall").value(false));
    }

    // ---- RT-002 / VS-002-repair binding cases (red-team re-attack vectors) ----

    @Test
    void check_casualTokenPlusGenuineCrisis_isBlocked_f1() throws Exception {
        // F1: a casual token (加班累死了) must NOT amnesty a genuine-crisis indicator
        // (累赘 + 如果我不在了). Real fallback (dev/mock mode) → HIGH block.
        SafetyCheckRequest request = new SafetyCheckRequest();
        request.text = "今天加班累死了，我真的是个累赘，如果我不在了大家会更好吧";

        mockMvc.perform(post("/api/safety/check")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 200) {
                        throw new AssertionError("F1: casual token must not amnesty genuine crisis, but got 200");
                    }
                });
    }

    @Test
    void inspect_casualTokenPlusGenuineCrisis_returnsHighBlock_f1() throws Exception {
        SafetyCheckRequest request = new SafetyCheckRequest();
        request.text = "今天加班累死了，我真的是个累赘，如果我不在了大家会更好吧";

        mockMvc.perform(post("/api/safety/inspect")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.data.blockModelCall").value(true));
    }

    @Test
    void check_casualVentingWith活着好累_isAllowed_f6() throws Exception {
        // F6 (VS-002's OWN acceptance example): "活着好累" is no longer a synchronous
        // block keyword; with only casual tokens present it must ALLOW.
        SafetyCheckRequest request = new SafetyCheckRequest();
        request.text = "今天加班累死了，活着好累";

        mockMvc.perform(post("/api/safety/check")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void check_literary绝望_isAllowed_f6() throws Exception {
        // F6: 绝望 removed from synchronous block list → literary use must ALLOW.
        SafetyCheckRequest request = new SafetyCheckRequest();
        request.text = "这本书的结局让我绝望";

        mockMvc.perform(post("/api/safety/check")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void check_ordinary结束_isAllowed_f6() throws Exception {
        // F6: 结束 removed from synchronous block list → ordinary use must ALLOW.
        SafetyCheckRequest request = new SafetyCheckRequest();
        request.text = "会议终于结束了";

        mockMvc.perform(post("/api/safety/check")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void check_bare想要了断_isBlocked_f12() throws Exception {
        // F12: crisis-leaning distress phrase (no explicit keyword) → fallback HIGH block.
        SafetyCheckRequest request = new SafetyCheckRequest();
        request.text = "想要了断";

        mockMvc.perform(post("/api/safety/check")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 200) {
                        throw new AssertionError("F12: 想要了断 should fallback-block, but got 200");
                    }
                });
    }

    @Test
    void check_bare想消失_isBlocked_f12() throws Exception {
        // F12: 想消失 is no longer a synchronous keyword but is crisis-leaning → fallback HIGH block.
        SafetyCheckRequest request = new SafetyCheckRequest();
        request.text = "想消失";

        mockMvc.perform(post("/api/safety/check")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 200) {
                        throw new AssertionError("F12: 想消失 should fallback-block, but got 200");
                    }
                });
    }

    @Test
    void check_explicit自杀_isBlocked_regression() throws Exception {
        // Regression: explicit crisis keyword still blocks synchronously.
        SafetyCheckRequest request = new SafetyCheckRequest();
        request.text = "我想要自杀";

        mockMvc.perform(post("/api/safety/check")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 200) {
                        throw new AssertionError("Regression: explicit 自杀 should block, but got 200");
                    }
                });
    }

    @Test
    void check_pureCasualHyperbole_isAllowed_regression() throws Exception {
        // Regression: pure tired/sleepy hyperbole (no crisis, no distress) must ALLOW.
        SafetyCheckRequest request = new SafetyCheckRequest();
        request.text = "今天加班累死了，困得要死";

        mockMvc.perform(post("/api/safety/check")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));
    }

    // ---------------- helpers ----------------

    private MockHttpSession loginWithUniqueUser() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "safetyuser_" + suffix;
        String password = "testPass123";

        String registerJson = "{\"username\":\"" + username + "\","
                + "\"password\":\"" + password + "\","
                + "\"nickname\":\"Safety Test\"}";

        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) regResult.getRequest().getSession(false);
        if (session == null) {
            String loginJson = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson))
                    .andExpect(status().isOk())
                    .andReturn();
            session = (MockHttpSession) loginResult.getRequest().getSession(false);
        }
        return session;
    }
}
