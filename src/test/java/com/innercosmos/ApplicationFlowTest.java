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

    @Test
    void auroraRichReplyThoughtShredderAndSafetyInspectionWork() throws Exception {
        MockHttpSession session = login();

        MvcResult createResult = mockMvc.perform(withSession(post("/api/dialog/session/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"rich\",\"sessionType\":\"AURORA_CHAT\"}"), session))
                .andExpect(status().isOk())
                .andReturn();
        String sessionId = readId(createResult);

        mockMvc.perform(withSession(post("/api/aurora/message-rich")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":" + sessionId + ",\"message\":\"今天项目任务拖延了，压力很大\",\"inputType\":\"VOICE\",\"audioDurationSec\":36,\"speechRate\":2.4,\"pauseCount\":3,\"longPauseCount\":1,\"mode\":\"ACTION_SPLIT\",\"timezone\":\"Asia/Shanghai\"}"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.detectedTheme").value("任务压力"))
                .andExpect(jsonPath("$.data.memoryReferenced").exists())
                .andExpect(jsonPath("$.data.memoryContext.memoryPolicy").exists())
                .andExpect(jsonPath("$.data.memoryContext.shortTermMessages.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.suggestSettle").exists());

        mockMvc.perform(withSession(get("/api/aurora/memory-context")
                        .param("sessionId", sessionId)
                        .param("q", "project deadline pressure"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memoryPolicy").exists())
                .andExpect(jsonPath("$.data.shortTermMessages.length()", greaterThanOrEqualTo(1)));

        mockMvc.perform(withSession(post("/api/thought-shredder/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"我真的很烦，作业一直拖延，感觉自己又没做好，明天必须交。\",\"originalHandlingMode\":\"KEEP_ONLY_RESULT\"}"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coreFeeling").exists())
                .andExpect(jsonPath("$.data.hiddenNeed").exists())
                .andExpect(jsonPath("$.data.noiseToDrop.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.fragments.length()", greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.data.suggestedTodo.taskName").exists())
                .andExpect(jsonPath("$.data.memoryCard.id").exists());

        mockMvc.perform(withSession(post("/api/safety/inspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"我要威胁并骚扰别人\"}"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.riskLevel").value("MEDIUM"))
                .andExpect(jsonPath("$.data.blockModelCall").value(false));

        mockMvc.perform(withSession(post("/api/safety/inspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"我不想活了\"}"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.data.blockModelCall").value(true));
    }

    @Test
    void memoryThemeAggregationWorks() throws Exception {
        MockHttpSession session = login();
        String[] messages = {
            "今天作业又没写完，压力很大",
            "项目任务拖延了，好烦",
            "考试快到了，一直没复习"
        };
        for (String msg : messages) {
            MvcResult createResult = mockMvc.perform(withSession(post("/api/dialog/session/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"theme-test\",\"sessionType\":\"AURORA_CHAT\"}"), session))
                    .andExpect(status().isOk())
                    .andReturn();
            String sid = readId(createResult);
            mockMvc.perform(withSession(post("/api/aurora/message")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sessionId\":" + sid + ",\"message\":\"" + msg + "\",\"inputType\":\"TEXT\"}"), session))
                    .andExpect(status().isOk());
            mockMvc.perform(withSession(post("/api/dialog/session/" + sid + "/finish"), session))
                    .andExpect(status().isOk());
        }
        Thread.sleep(800);
        mockMvc.perform(withSession(get("/api/memory/themes"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void starfieldDetailFlowWorks() throws Exception {
        MockHttpSession session = login();
        MvcResult createResult = mockMvc.perform(withSession(post("/api/dialog/session/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"detail-test\",\"sessionType\":\"AURORA_CHAT\"}"), session))
                .andExpect(status().isOk())
                .andReturn();
        String sessionId = readId(createResult);
        mockMvc.perform(withSession(post("/api/aurora/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":" + sessionId + ",\"message\":\"今天有点累\",\"inputType\":\"TEXT\"}"), session))
                .andExpect(status().isOk());
        mockMvc.perform(withSession(post("/api/dialog/session/" + sessionId + "/finish"), session))
                .andExpect(status().isOk());
        Thread.sleep(500);
        MvcResult starResult = mockMvc.perform(withSession(get("/api/memory/starfield"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)))
                .andReturn();
        JsonNode starData = objectMapper.readTree(starResult.getResponse().getContentAsString()).path("data");
        String cardId = starData.get(0).path("detail").path("id").asText();
        if ("0".equals(cardId) || cardId.isEmpty()) {
            cardId = starData.get(0).path("id").asText();
        }
        mockMvc.perform(withSession(get("/api/memory/starfield/" + cardId + "/detail"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.card").exists());
    }

    @Test
    void remoteLlmFallbackWorks() throws Exception {
        MockHttpSession session = login();
        MvcResult createResult = mockMvc.perform(withSession(post("/api/dialog/session/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"fallback-test\",\"sessionType\":\"AURORA_CHAT\"}"), session))
                .andExpect(status().isOk())
                .andReturn();
        String sessionId = readId(createResult);
        mockMvc.perform(withSession(post("/api/aurora/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":" + sessionId + ",\"message\":\"测试 fallback\",\"inputType\":\"TEXT\"}"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNotEmpty());
        mockMvc.perform(withSession(get("/api/ai-logs"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data[0].provider").exists())
                .andExpect(jsonPath("$.data[0].modelName").exists())
                .andExpect(jsonPath("$.data[0].fallbackUsed").exists());

        mockMvc.perform(withSession(get("/api/ai/health"), session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider").value("minimax"))
                .andExpect(jsonPath("$.data.apiKeyConfigured").value(false))
                .andExpect(jsonPath("$.data.fallbackAllowed").value(true));
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
