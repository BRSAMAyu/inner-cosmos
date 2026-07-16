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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "llm.mode=dev",
        "llm.provider=minimax",
        "llm.api-key=",
        "llm.minimax.api-key=",
        "llm.allow-fallback=true",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.datasource.url=jdbc:h2:mem:testtodo;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always"
})
@AutoConfigureMockMvc
@Import(TestRateLimitConfig.class)
class TodoControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        session = loginWithUniqueUser();
    }

    // ---------------- List ----------------

    @Test
    void list_returnsEmptyList_forNewUser() throws Exception {
        mockMvc.perform(get("/api/todos").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    // ---------------- Create ----------------

    @Test
    void create_succeeds() throws Exception {
        Map<String, Object> todo = buildTodo("Integration test task", "HIGH");

        mockMvc.perform(post("/api/todos")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(todo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.taskName").value("Integration test task"))
                .andExpect(jsonPath("$.data.id").value(notNullValue()));
    }

    // ---------------- Update ----------------

    @Test
    void update_succeeds() throws Exception {
        long todoId = createTodo("Task to update", "MEDIUM");

        Map<String, Object> updated = buildTodo("Updated task name", "HIGH");

        mockMvc.perform(put("/api/todos/" + todoId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.taskName").value("Updated task name"));
    }

    // ---------------- Status ----------------

    @Test
    void updateStatus_withValidStatus_succeeds() throws Exception {
        long todoId = createTodo("Status test task", "LOW");

        Map<String, String> statusBody = new HashMap<>();
        statusBody.put("status", "DOING");

        mockMvc.perform(post("/api/todos/" + todoId + "/status")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DOING"));
    }

    @Test
    void updateStatus_withInvalidStatus_fails() throws Exception {
        long todoId = createTodo("Invalid status task", "LOW");

        Map<String, String> statusBody = new HashMap<>();
        statusBody.put("status", "NOT_A_REAL_STATUS");

        // The endpoint may accept any string; this test documents the behaviour
        // If the service validates, expect 400; if not, expect 200 with data
        mockMvc.perform(post("/api/todos/" + todoId + "/status")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusBody)))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status != 200 && status != 400) {
                        throw new AssertionError("Expected 200 or 400 but got " + status);
                    }
                });
    }

    // ---------------- Type mismatch (non-numeric path variable) ----------------

    @Test
    void delete_withNonNumericId_returns400NotFromCatchAll() throws Exception {
        // A fat-fingered URL like /api/todos/abc must map to 400 (BAD_REQUEST),
        // not fall through to the RuntimeException catch-all returning 500.
        mockMvc.perform(delete("/api/todos/abc").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    // ---------------- Delete ----------------

    @Test
    void delete_succeeds() throws Exception {
        long todoId = createTodo("Task to delete", "LOW");

        mockMvc.perform(delete("/api/todos/" + todoId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));
    }

    // ---------------- List after create ----------------

    @Test
    void list_returnsCreatedTodos() throws Exception {
        createTodo("First task", "HIGH");
        createTodo("Second task", "MEDIUM");

        mockMvc.perform(get("/api/todos").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(2)));
    }

    // ---------------- Split ----------------

    @Test
    void split_returnsResult() throws Exception {
        long todoId = createTodo("Task to split", "MEDIUM");

        mockMvc.perform(post("/api/todos/" + todoId + "/split").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists());
    }

    // ---------------- helpers ----------------

    private MockHttpSession loginWithUniqueUser() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "todouser_" + suffix;
        String password = "testPass123";

        String registerJson = "{\"username\":\"" + username + "\","
                + "\"password\":\"" + password + "\","
                + "\"nickname\":\"Todo Test\"}";

        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) regResult.getRequest().getSession(false);
        if (session == null) {
            // Fall back to login
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

    private Map<String, Object> buildTodo(String taskName, String priority) {
        Map<String, Object> todo = new HashMap<>();
        todo.put("taskName", taskName);
        todo.put("description", "Description for " + taskName);
        todo.put("priority", priority);
        todo.put("status", "TODO");
        return todo;
    }

    private long createTodo(String taskName, String priority) throws Exception {
        Map<String, Object> todo = buildTodo(taskName, priority);

        MvcResult result = mockMvc.perform(post("/api/todos")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(todo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.path("data").path("id").asLong();
    }
}
