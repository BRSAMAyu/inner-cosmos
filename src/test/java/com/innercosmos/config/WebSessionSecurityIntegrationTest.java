package com.innercosmos.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.common.Constants;
import com.innercosmos.dto.LoginRequest;
import com.innercosmos.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "inner-cosmos.security.csrf-enabled=true",
        "inner-cosmos.demo.seed-enabled=true",
        "spring.datasource.url=jdbc:h2:mem:web-session-security;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always"
})
@AutoConfigureMockMvc
class WebSessionSecurityIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    private String username;
    private String password;

    @BeforeEach
    void setUp() {
        username = "security_" + UUID.randomUUID().toString().substring(0, 8);
        password = "security-password-123";
    }

    @Test
    void csrfEndpointMaterializesBrowserTokenAndUnsafeRequestRequiresIt() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.headerName").value("X-CSRF-TOKEN"));

        RegisterRequest register = registerRequest();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("CSRF_INVALID"));

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isOk());
    }

    @Test
    void registrationRotatesAttackerSuppliedSessionId() throws Exception {
        MockHttpSession attackerSession = new MockHttpSession();
        String attackerSessionId = attackerSession.getId();

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .session(attackerSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest())))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession authenticated = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(authenticated).isNotNull();
        assertThat(authenticated.getId()).isNotEqualTo(attackerSessionId);
        assertThat(authenticated.getAttribute(Constants.SESSION_USER_KEY)).isInstanceOf(Long.class);
    }

    @Test
    void loginRotatesPreAuthenticationSessionAndFreshTokenAuthorizesNextMutation() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest())))
                .andExpect(status().isOk());

        MockHttpSession preAuthentication = new MockHttpSession();
        String oldId = preAuthentication.getId();
        LoginRequest login = new LoginRequest();
        login.username = username;
        login.password = password;

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .session(preAuthentication)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession authenticated = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(authenticated).isNotNull();
        assertThat(authenticated.getId()).isNotEqualTo(oldId);
        assertThat(authenticated.getAttribute(Constants.SESSION_USER_KEY)).isInstanceOf(Long.class);

        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf").session(authenticated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headerName").value("X-CSRF-TOKEN"))
                .andReturn();
        String token = objectMapper.readTree(csrfResult.getResponse().getContentAsString())
                .path("data").path("token").asText();

        mockMvc.perform(post("/api/auth/logout")
                        .session(authenticated)
                        .header("X-CSRF-TOKEN", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void identityHeaderCannotAuthenticateAndSecurityErrorsUseJsonEnvelope() throws Exception {
        mockMvc.perform(get("/api/user/profile").header("X-User-Id", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void authenticatedLogoutStillRequiresCsrfAndValidTokenInvalidatesSession() throws Exception {
        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest())))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) registration.getRequest().getSession(false);

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("CSRF_INVALID"));

        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/current").session(session))
                .andExpect(status().isUnauthorized());
    }

    private RegisterRequest registerRequest() {
        RegisterRequest register = new RegisterRequest();
        register.username = username;
        register.password = password;
        register.nickname = "Security Contract";
        return register;
    }
}
