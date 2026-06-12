package com.innercosmos.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.config.TestRateLimitConfig;
import com.innercosmos.dto.LoginRequest;
import com.innercosmos.dto.RegisterRequest;
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
        "spring.datasource.url=jdbc:h2:mem:testauth;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always"
})
@AutoConfigureMockMvc
@Import(TestRateLimitConfig.class)
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    private String testUsername;
    private String testPassword;

    @BeforeEach
    void setUp() {
        // Use a unique username per test to avoid duplicate conflicts when DB state persists
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        testUsername = "user_" + suffix;
        testPassword = "password123";
    }

    // ---------------- Register ----------------

    @Test
    void register_withValidData_succeeds() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.username = testUsername;
        request.password = testPassword;
        request.nickname = "Test User";
        request.email = testUsername + "@example.com";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.username").value(testUsername));
    }

    @Test
    void register_withMissingUsername_failsValidation() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.password = testPassword;
        request.nickname = "NoUsername";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void register_withShortPassword_failsValidation() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.username = testUsername;
        request.password = "short"; // < 8 chars
        request.nickname = "ShortPw";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void register_withDuplicateUsername_fails() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.username = testUsername;
        request.password = testPassword;
        request.nickname = "First";

        // First registration succeeds
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Second registration with the same username should fail
        RegisterRequest duplicate = new RegisterRequest();
        duplicate.username = testUsername;
        duplicate.password = testPassword;
        duplicate.nickname = "Second";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isBadRequest());
    }

    // ---------------- Login ----------------

    @Test
    void login_withValidCredentials_succeeds() throws Exception {
        // Register first
        RegisterRequest register = new RegisterRequest();
        register.username = testUsername;
        register.password = testPassword;
        register.nickname = "LoginTest";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isOk());

        // Login
        LoginRequest login = new LoginRequest();
        login.username = testUsername;
        login.password = testPassword;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value(testUsername));
    }

    @Test
    void login_withWrongPassword_fails() throws Exception {
        // Register first
        RegisterRequest register = new RegisterRequest();
        register.username = testUsername;
        register.password = testPassword;
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isOk());

        // Attempt login with wrong password
        LoginRequest login = new LoginRequest();
        login.username = testUsername;
        login.password = "wrongPassword123";

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_withNonExistentUser_fails() throws Exception {
        LoginRequest login = new LoginRequest();
        login.username = "no_such_user_" + UUID.randomUUID();
        login.password = testPassword;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isBadRequest());
    }

    // ---------------- Logout ----------------

    @Test
    void logout_succeeds() throws Exception {
        MockHttpSession session = registerAndLogin();

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));
    }

    // ---------------- Current User ----------------

    @Test
    void current_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/current"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void current_returnsUserData_whenAuthenticated() throws Exception {
        MockHttpSession session = registerAndLogin();

        mockMvc.perform(get("/api/auth/current").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.username").value(testUsername))
                .andExpect(jsonPath("$.data.id").value(notNullValue()));
    }

    // ---------------- helpers ----------------

    private MockHttpSession registerAndLogin() throws Exception {
        RegisterRequest register = new RegisterRequest();
        register.username = testUsername;
        register.password = testPassword;
        register.nickname = "Helper";

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        if (session == null) {
            // Registration does not always bind a session - fall back to login
            LoginRequest login = new LoginRequest();
            login.username = testUsername;
            login.password = testPassword;
            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(login)))
                    .andExpect(status().isOk())
                    .andReturn();
            session = (MockHttpSession) loginResult.getRequest().getSession(false);
        }
        return session;
    }
}
