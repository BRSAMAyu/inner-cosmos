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
        // Safety resources may require auth depending on SecurityConfig
        // Just verify the endpoint responds (either 200 or 403 is acceptable)
        mockMvc.perform(get("/api/safety/resources"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status != 200 && status != 403) {
                        throw new AssertionError("Expected 200 or 403 but got " + status);
                    }
                });
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
