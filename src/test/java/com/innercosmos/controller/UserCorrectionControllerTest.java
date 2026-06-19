package com.innercosmos.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.config.TestRateLimitConfig;
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

    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        session = loginWithUniqueUser();
    }

    @Test
    void corrections_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/aurora/corrections"))
                .andExpect(status().isForbidden());
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
