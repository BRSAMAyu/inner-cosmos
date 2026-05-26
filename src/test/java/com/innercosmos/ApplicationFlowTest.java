package com.innercosmos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import org.springframework.mock.web.MockHttpSession;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationFlowTest {
    @Autowired
    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fullUserJourneyWorksInMockMode() throws Exception {
        MockHttpSession session = login();

        MvcResult createResult = mockMvc.perform(withSession(post("/api/dialog/session/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"test\",\"sessionType\":\"AURORA_CHAT\"}"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        String sessionId = readId(createResult);

        mockMvc.perform(withSession(post("/api/aurora/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":" + sessionId + ",\"message\":\"今天有点累，作业也拖延了\",\"inputType\":\"TEXT\"}"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(withSession(post("/api/dialog/session/" + sessionId + "/finish"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Thread.sleep(500);

        mockMvc.perform(withSession(get("/api/daily-record/latest"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fragments.length()", greaterThanOrEqualTo(1)));

        mockMvc.perform(withSession(get("/api/memory/starfield"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data[0].theme").exists())
                .andExpect(jsonPath("$.data[0].color").exists())
                .andExpect(jsonPath("$.data[0].detail").exists());
    }

    @Test
    void plazaPersonaChatAndLetterFlowWork() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/api/plaza/capsules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(5)));

        MvcResult chatSession = mockMvc.perform(withSession(post("/api/persona-chat/session/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capsuleId\":1}"), session))
                .andExpect(status().isOk())
                .andReturn();
        String chatSessionId = readId(chatSession);

        mockMvc.perform(withSession(post("/api/persona-chat/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":" + chatSessionId + ",\"message\":\"我想问问怎么面对自责\"}"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.senderType").value("CAPSULE"));

        MvcResult letter = mockMvc.perform(withSession(post("/api/letters/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverUserId\":1,\"receiverCapsuleId\":1,\"title\":\"测试慢信\",\"letterBody\":\"我在这里感到一点共鸣。\"}"), session))
                .andExpect(status().isOk())
                .andReturn();
        String letterId = readId(letter);

        mockMvc.perform(withSession(post("/api/letters/" + letterId + "/send"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SENT"));
    }

    @Test
    void capsuleBoundaryAndVisibilityFlowWork() throws Exception {
        MockHttpSession session = login();

        MvcResult capsule = mockMvc.perform(withSession(post("/api/capsule/create-from-memory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pseudonym\":\"柔雾回声\",\"intro\":\"只谈自我观察，不暴露身份。\",\"memoryIds\":[],\"allowTopics\":[\"自我观察\",\"日常支持\"],\"blockedTopics\":[\"真实姓名\",\"诊断承诺\"],\"maxConversationTurns\":7,\"allowLetterRequest\":false,\"privacyLevel\":\"STRICT\",\"visibilityStatus\":\"PRIVATE\",\"isPublic\":false}"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visibilityStatus").value("PRIVATE"))
                .andExpect(jsonPath("$.data.isPublic").value(false))
                .andReturn();
        String capsuleId = readId(capsule);

        mockMvc.perform(withSession(post("/api/capsule/" + capsuleId + "/visibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibilityStatus\":\"PUBLIC\",\"isPublic\":true}"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visibilityStatus").value("PUBLIC"))
                .andExpect(jsonPath("$.data.isPublic").value(true));
    }

    private MockHttpSession login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo\",\"password\":\"demo123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpServletRequestBuilder withSession(MockHttpServletRequestBuilder builder, MockHttpSession session) {
        return session == null ? builder : builder.session(session);
    }

    private String readId(MvcResult result) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.path("data").path("id").asText();
    }
}
