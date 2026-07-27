package com.innercosmos.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.config.TestRateLimitConfig;
import com.innercosmos.entity.User;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:public-demo-template-protection;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always",
        "spring.task.scheduling.enabled=false",
        "inner-cosmos.demo.seed-enabled=true",
        "inner-cosmos.demo.public-entry-enabled=true",
        "inner-cosmos.demo.template-password-login-enabled=false",
        "memory.embedding.enabled=false",
        "llm.provider=mock"
})
@AutoConfigureMockMvc
@Import(TestRateLimitConfig.class)
class PublicDemoTemplateProtectionTest {
    @Autowired MockMvc mockMvc;
    @Autowired UserService userService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void curatedTemplatePasswordLoginIsRejectedBeforeItCanMutateSharedProfile() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"demo","password":"demo123","timezone":"Pacific/Honolulu"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void templatePasswordAndAccountRemainImmutableAtTheServiceBoundary() {
        User template = userService.findPublicDemoPersona("demo");
        assertThat(template).isNotNull();

        assertThatThrownBy(() -> userService.changePassword(
                template.id, "demo123", "changed-demo-password"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> userService.deleteAccount(template.id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be deleted");

        assertThat(userService.findPublicDemoPersona("demo")).isNotNull();
    }

    @Test
    void separateBrowserEntriesStillReceiveSeparateDisposableSandboxes() throws Exception {
        MockHttpSession firstSession = new MockHttpSession();
        MockHttpSession secondSession = new MockHttpSession();

        JsonNode first = enterStory(firstSession, "lin-che");
        JsonNode second = enterStory(secondSession, "lin-che");

        assertThat(first.path("id").asLong()).isPositive();
        assertThat(second.path("id").asLong()).isPositive();
        assertThat(first.path("id").asLong()).isNotEqualTo(second.path("id").asLong());
        assertThat(first.path("username").asText()).startsWith("sandbox-");
        assertThat(second.path("username").asText()).startsWith("sandbox-");

        mockMvc.perform(delete("/api/public/demo/sandbox").session(firstSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
        mockMvc.perform(delete("/api/public/demo/sandbox").session(secondSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
        assertThat(userService.findPublicDemoPersona("demo")).isNotNull();
    }

    private JsonNode enterStory(MockHttpSession session, String key) throws Exception {
        String response = mockMvc.perform(post("/api/public/demo/enter/" + key)
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }
}
