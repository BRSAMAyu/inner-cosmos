package com.innercosmos.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 2026-07-24/07-27 audit (P1, CONFIRMED): {@code /actuator/metrics} and {@code /actuator/info}
 * were {@code permitAll()}, so any anonymous caller reaching a deployed environment's Gateway
 * (deploy/k8s/overlays/academy-eks/http-route.yml routes {@code PathPrefix: /} straight to the
 * app's port, and {@code management.endpoints.web.exposure.include} lists
 * {@code health,metrics,prometheus,info}) could scrape per-URI request counts, AI-provider
 * latency histograms, SSE connection counts and JVM/DB-pool internals with zero credential.
 *
 * <p>Pins: anonymous access to metrics/info is denied; an authenticated non-admin user is still
 * denied (this is an ADMIN-only surface, not merely "logged in"); an authenticated ADMIN can
 * still reach both; and {@code /actuator/health}/{@code /actuator/prometheus} remain reachable
 * without authentication (Kubernetes probes, the documented local dev quick-start, and
 * credential-less in-cluster Prometheus scraping all depend on that -- see SecurityConfig's
 * comment on why {@code /actuator/prometheus} is a deliberate, judged exception).
 */
// src/test/resources/application.yml fully shadows src/main/resources/application.yml on the test
// classpath (Maven puts target/test-classes ahead of target/classes, and classpath:/application.yml
// resolves to a single resource) -- so the real management.endpoints.web.exposure.include list this
// class's Javadoc describes NEVER reaches the test JVM on its own; without restating it here every
// endpoint except the Spring Boot default ("health") 404s regardless of the security decision,
// making the ADMIN-gating assertions below untestable. Production is unaffected: a packaged jar has
// no src/test/resources on its classpath. (2026-07-27 build-convergence audit)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "management.endpoints.web.exposure.include=health,metrics,prometheus,info",
        // src/test/resources/application.yml shadows the production file. Pin exporter creation
        // as well as endpoint exposure so this test exercises the real scrape handler, not a 404.
        "management.prometheus.metrics.export.enabled=true"
})
class ActuatorExposureTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void anonymousRequestToMetricsIsDenied() throws Exception {
        assertDenied(mockMvc.perform(get("/actuator/metrics")).andReturn());
        assertDenied(mockMvc.perform(get("/actuator/metrics/jvm.memory.used")).andReturn());
    }

    @Test
    void anonymousRequestToInfoIsDenied() throws Exception {
        assertDenied(mockMvc.perform(get("/actuator/info")).andReturn());
    }

    @Test
    void authenticatedNonAdminIsStillDeniedMetricsAndInfo() throws Exception {
        MockHttpSession user = registerAndLogin();
        mockMvc.perform(get("/actuator/metrics").session(user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(get("/actuator/info").session(user))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedAdminCanStillReachMetricsAndInfo() throws Exception {
        MockHttpSession admin = login("admin", "admin123");
        mockMvc.perform(get("/actuator/metrics").session(admin))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/info").session(admin))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousHealthProbeRemainsReachable() throws Exception {
        // Kubernetes liveness/readiness (deploy/k8s/base/app-deployment.yml) and the CLAUDE.md-
        // documented local dev quick start both poll this with no credential. Assert it is never
        // blocked by authorization (401/403); its own UP/DOWN value depends on live dependency
        // health (DB/Redis), which is out of scope for this access-control test.
        MvcResult result = mockMvc.perform(get("/actuator/health")).andReturn();
        assertThat(result.getResponse().getStatus())
                .as("actuator/health must never require authentication")
                .isNotIn(401, 403);
    }

    @Test
    void anonymousPrometheusScrapeRemainsReachable() throws Exception {
        // Deliberately still permitAll (see SecurityConfig's comment): Prometheus scrapes this
        // with no credential configured, so gating it would break in-cluster scraping outright.
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk());
    }

    private void assertDenied(MvcResult result) {
        int status = result.getResponse().getStatus();
        // Spring Security's ExceptionTranslationFilter routes an *anonymous* principal's access
        // denial through the authenticationEntryPoint (401 UNAUTHORIZED) rather than the
        // accessDeniedHandler (403 FORBIDDEN) -- see WebSessionSecurityIntegrationTest's
        // identityHeaderCannotAuthenticateAndSecurityErrorsUseJsonEnvelope for the same pattern
        // against a plain .authenticated() rule. Either way, it must never be 200.
        assertThat(status)
                .as("an anonymous actuator/metrics or actuator/info request must not succeed")
                .isIn(401, 403);
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private MockHttpSession registerAndLogin() throws Exception {
        String username = "actuator_" + UUID.randomUUID().toString().substring(0, 8);
        String body = "{\"username\":\"" + username + "\",\"password\":\"actuator-password-1\","
                + "\"nickname\":\"Actuator Test\"}";
        MvcResult registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) registration.getRequest().getSession(false);
    }
}
