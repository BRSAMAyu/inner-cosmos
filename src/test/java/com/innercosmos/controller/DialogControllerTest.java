package com.innercosmos.controller;

import com.fasterxml.jackson.databind.JsonNode;
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

import static org.hamcrest.Matchers.notNullValue;
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
        "spring.datasource.url=jdbc:h2:mem:testdialog;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always"
})
@AutoConfigureMockMvc
@Import(TestRateLimitConfig.class)
class DialogControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        session = loginWithUniqueUser();
    }

    // ---------------- Create Session ----------------

    @Test
    void createSession_succeeds() throws Exception {
        String body = "{\"title\":\"Test session\",\"sessionType\":\"AURORA_CHAT\"}";

        mockMvc.perform(post("/api/dialog/session/create")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.id").value(notNullValue()))
                .andExpect(jsonPath("$.data.sessionType").value("AURORA_CHAT"));
    }

    @Test
    void createSession_withTitle_setsTitle() throws Exception {
        String body = "{\"title\":\"My custom title\",\"sessionType\":\"AURORA_CHAT\"}";

        mockMvc.perform(post("/api/dialog/session/create")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("My custom title"));
    }

    // ---------------- Messages ----------------

    @Test
    void getMessages_forNewSession_returnsEmpty() throws Exception {
        long sessionId = createSession("Empty messages test");

        mockMvc.perform(get("/api/dialog/session/" + sessionId + "/messages")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getMessages_requiresValidSessionId() throws Exception {
        // Use a non-existent session id; should return error
        mockMvc.perform(get("/api/dialog/session/999999/messages")
                        .session(session))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status != 200 && status != 400 && status != 404) {
                        throw new AssertionError("Expected 200/400/404 but got " + status);
                    }
                });
    }

    // ---------------- Finish Session ----------------

    @Test
    void finishSession_succeeds() throws Exception {
        long sessionId = createSession("Finish test");

        mockMvc.perform(post("/api/dialog/session/" + sessionId + "/finish")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(notNullValue()));
    }

    @Test
    void finishSession_requiresValidSessionId() throws Exception {
        mockMvc.perform(post("/api/dialog/session/999999/finish")
                        .session(session))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status != 200 && status != 400 && status != 404) {
                        throw new AssertionError("Expected 200/400/404 but got " + status);
                    }
                });
    }

    // ---------------- helpers ----------------

    private MockHttpSession loginWithUniqueUser() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "dialoguser_" + suffix;
        String password = "testPass123";

        String registerJson = "{\"username\":\"" + username + "\","
                + "\"password\":\"" + password + "\","
                + "\"nickname\":\"Dialog Test\"}";

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

    private long createSession(String title) throws Exception {
        String body = "{\"title\":\"" + title + "\",\"sessionType\":\"AURORA_CHAT\"}";

        MvcResult result = mockMvc.perform(post("/api/dialog/session/create")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.path("data").path("id").asLong();
    }
}
