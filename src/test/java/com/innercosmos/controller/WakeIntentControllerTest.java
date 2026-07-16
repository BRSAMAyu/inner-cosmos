package com.innercosmos.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.common.Constants;
import com.innercosmos.config.TestRateLimitConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.task.scheduling.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:testwakecontroller;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.sql.init.mode=always"
})
@AutoConfigureMockMvc
@Import(TestRateLimitConfig.class)
class WakeIntentControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void createListRescheduleCancelAreOwnerScoped() throws Exception {
        MockHttpSession owner = register("wake-owner");
        MockHttpSession other = register("wake-other");
        LocalDateTime preferred = LocalDateTime.now().plusHours(2).withNano(0);
        String body = """
            {"purpose":"继续今天的话题","reasonForUser":"Aurora 会按约回来", "content":"我来赴约了。",
             "earliestAt":"%s","preferredAt":"%s","latestAt":"%s","timezone":"Asia/Shanghai"}
            """.formatted(preferred.minusMinutes(5), preferred, preferred.plusHours(3));

        MvcResult created = mockMvc.perform(post("/api/aurora/wake-intents").session(owner)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PLANNED"))
            .andExpect(jsonPath("$.data.reasonForUser").value("Aurora 会按约回来"))
            .andReturn();
        JsonNode payload = objectMapper.readTree(created.getResponse().getContentAsString());
        long id = payload.path("data").path("id").asLong();

        mockMvc.perform(get("/api/aurora/wake-intents").session(owner))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(id));
        mockMvc.perform(get("/api/aurora/wake-intents").session(other))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mockMvc.perform(post("/api/aurora/wake-intents/{id}/cancel", id).session(other))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));

        LocalDateTime moved = preferred.plusDays(1);
        MvcResult rescheduled = mockMvc.perform(put("/api/aurora/wake-intents/{id}/schedule", id).session(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"earliestAt\":\"%s\",\"preferredAt\":\"%s\",\"latestAt\":\"%s\"}"
                    .formatted(moved.minusMinutes(5), moved, moved.plusHours(2))))
            .andExpect(status().isOk()).andReturn();
        // Compare as parsed instants, not as strings: LocalDateTime.toString() elides the seconds
        // field when it is :00 (i.e. when now().getSecond()==0), while the server always serializes
        // HH:mm:ss — a raw string match flakes ~1/60 runs. ISO parse normalizes both forms.
        String returnedPreferred = objectMapper.readTree(rescheduled.getResponse().getContentAsString())
            .path("data").path("preferredAt").asText();
        assertThat(LocalDateTime.parse(returnedPreferred)).isEqualTo(moved);
        mockMvc.perform(post("/api/aurora/wake-intents/{id}/cancel", id).session(owner))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    private MockHttpSession register(String prefix) throws Exception {
        String username = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"testPass123\",\"nickname\":\"Wake Test\"}"
                    .formatted(username)))
            .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        if (session == null || session.getAttribute(Constants.SESSION_USER_KEY) == null) {
            result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"%s\",\"password\":\"testPass123\"}".formatted(username)))
                .andExpect(status().isOk()).andReturn();
            session = (MockHttpSession) result.getRequest().getSession(false);
        }
        return session;
    }
}
