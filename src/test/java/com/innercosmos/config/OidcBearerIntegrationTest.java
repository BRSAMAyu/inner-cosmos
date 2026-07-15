package com.innercosmos.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.dto.RegisterRequest;
import com.innercosmos.entity.UserIdentity;
import com.innercosmos.entity.User;
import com.innercosmos.mapper.UserIdentityMapper;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.ratelimit.RateLimitDecision;
import com.innercosmos.ratelimit.RateLimitKey;
import com.innercosmos.ratelimit.RateLimitStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "inner-cosmos.auth.oidc.enabled=true",
        "inner-cosmos.auth.oidc.issuer-uri=https://identity.example/",
        "inner-cosmos.auth.oidc.jwk-set-uri=https://identity.example/jwks",
        "inner-cosmos.auth.oidc.audience=inner-cosmos-api",
        "inner-cosmos.auth.oidc.authorization-uri=https://identity.example/authorize",
        "inner-cosmos.auth.oidc.token-uri=https://identity.example/token",
        "inner-cosmos.auth.oidc.client-id=inner-cosmos-mobile",
        "inner-cosmos.auth.oidc.redirect-uri=innercosmos://auth/callback",
        "inner-cosmos.security.csrf-enabled=true",
        "inner-cosmos.demo.seed-enabled=false",
        "spring.datasource.url=jdbc:h2:mem:oidc-security;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always"
})
@AutoConfigureMockMvc
class OidcBearerIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired UserIdentityMapper identityMapper;
    @Autowired UserMapper userMapper;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean RateLimitStore rateLimitStore;

    @BeforeEach
    void decodeFixtures() {
        identityMapper.delete(null);
        userMapper.delete(new QueryWrapper<User>().likeRight("username", "oidc_"));
        when(rateLimitStore.consume(anyString(), any())).thenReturn(new RateLimitDecision(true, 10));
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> switch (invocation.getArgument(0, String.class)) {
            case "valid-user" -> jwt("valid-user", "subject-123", true);
            case "valid-user-again" -> jwt("valid-user-again", "subject-123", true);
            case "unverified-email" -> jwt("unverified-email", "subject-456", false);
            default -> throw new BadJwtException("test invalid token");
        });
    }

    @Test
    void bearerAutoProvisionsStableLocalUserWithoutTrustingClientRole() throws Exception {
        mockMvc.perform(get("/api/auth/current").header("Authorization", "Bearer valid-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(org.hamcrest.Matchers.startsWith("oidc_")))
                .andExpect(jsonPath("$.data.role").value("USER"));

        mockMvc.perform(get("/api/auth/current").header("Authorization", "Bearer valid-user-again"))
                .andExpect(status().isOk());

        List<UserIdentity> identities = identityMapper.selectList(null);
        assertThat(identities).hasSize(1);
        assertThat(userMapper.selectById(identities.get(0).userId).role).isEqualTo("USER");
        assertThat(userMapper.selectById(identities.get(0).userId).email).isEqualTo("person@example.com");
    }

    @Test
    void bearerMutationDoesNotRequireBrowserCsrfButUnverifiedEmailIsNotStored() throws Exception {
        mockMvc.perform(post("/api/todos")
                        .header("Authorization", "Bearer unverified-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskName\":\"PKCE login follow-up\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").isNumber());

        UserIdentity identity = identityMapper.selectList(null).stream()
                .filter(value -> "subject-456".equals(value.subject)).findFirst().orElseThrow();
        assertThat(identity.emailSnapshot).isNull();
        assertThat(userMapper.selectById(identity.userId).email).isNull();
    }

    @Test
    void invalidBearerCannotUseCookieSessionToBypassCsrf() throws Exception {
        RegisterRequest register = new RegisterRequest();
        register.username = "oidc_session_" + UUID.randomUUID().toString().substring(0, 8);
        register.password = "session-password-123";
        register.nickname = "Session User";
        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(register)))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);

        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/current").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void validBearerTakesPrecedenceOverAnUnrelatedBrowserSession() throws Exception {
        RegisterRequest register = new RegisterRequest();
        register.username = "oidc_session_" + UUID.randomUUID().toString().substring(0, 8);
        register.password = "session-password-123";
        register.nickname = "Session User";
        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(register)))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
        Long sessionUserId = (Long) session.getAttribute(com.innercosmos.common.Constants.SESSION_USER_KEY);
        clearInvocations(rateLimitStore);

        mockMvc.perform(post("/api/todos")
                        .session(session)
                        .header("Authorization", "Bearer valid-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskName\":\"Bearer identity wins\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(org.hamcrest.Matchers.not(sessionUserId.intValue())));

        UserIdentity bearerIdentity = identityMapper.selectList(null).stream()
                .filter(value -> "subject-123".equals(value.subject)).findFirst().orElseThrow();
        verify(rateLimitStore).consume(eq(RateLimitKey.forSubject("user", bearerIdentity.userId.toString())), any());
    }

    @Test
    void publicBootstrapPublishesPkceS256WithoutAClientSecret() throws Exception {
        mockMvc.perform(get("/api/public/auth/mobile-oidc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.flow").value("authorization_code"))
                .andExpect(jsonPath("$.data.pkceRequired").value(true))
                .andExpect(jsonPath("$.data.codeChallengeMethod").value("S256"))
                .andExpect(jsonPath("$.data.clientId").value("inner-cosmos-mobile"))
                .andExpect(jsonPath("$.data.clientSecret").doesNotExist());
    }

    private static Jwt jwt(String token, String subject, boolean emailVerified) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .issuer("https://identity.example/")
                .subject(subject)
                .audience(List.of("inner-cosmos-api"))
                .issuedAt(now.minusSeconds(10))
                .expiresAt(now.plusSeconds(300))
                .claim("name", "OIDC Person")
                .claim("email", "person@example.com")
                .claim("email_verified", emailVerified)
                .claim("roles", List.of("ADMIN"))
                .build();
    }
}
