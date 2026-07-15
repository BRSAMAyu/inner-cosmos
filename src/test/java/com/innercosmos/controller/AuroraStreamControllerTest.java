package com.innercosmos.controller;

import com.innercosmos.config.TestRateLimitConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;

/**
 * VS-003 — end-to-end SSE wire trace for /api/aurora/stream.
 * Captures the actual SSE bytes for (a) a normal mock-mode reply — proving chunks
 * arrive over real SSE transport, and (b) a crisis input — proving a `safety` event
 * is emitted and NO chat `token`/content streams.
 */
@SpringBootTest(properties = {
        "llm.mode=dev",
        "llm.provider=minimax",
        "llm.api-key=",
        "llm.minimax.api-key=",
        "llm.allow-fallback=true",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.datasource.url=jdbc:h2:mem:testaurorastream;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always"
})
@AutoConfigureMockMvc
@Import(TestRateLimitConfig.class)
class AuroraStreamControllerTest {

    @Autowired
    MockMvc mockMvc;

    private MockHttpSession session;
    private long sessionId;

    @BeforeEach
    void setUp() throws Exception {
        session = loginWithUniqueUser();
        sessionId = createSession();
    }

    @Test
    @DisplayName("normal message streams token chunks + meta over real SSE (mock mode)")
    void stream_normal_emitsTokenChunks() throws Exception {
        String message = "今天有点累，想聊聊";
        String body = performStream(message);

        // Trace evidence: token chunks carrying content arrive over SSE.
        assertTrue(body.contains("event:token") || body.contains("\"content\""),
                "expected streamed token content; got:\n" + body);
        // Meta event closes the stream.
        assertTrue(body.contains("event:meta"), "expected meta event; got:\n" + body);
        // INNO-CONV-001: explicit lifecycle types and replay/correlation IDs are
        // additive; unnamed content deltas above remain for the existing client.
        assertTrue(body.contains("event:turn.plan"), "expected durable turn plan event; got:\n" + body);
        assertTrue(body.contains("event:turn.started"), "expected cancellable generation event; got:\n" + body);
        assertTrue(body.contains("event:bubble.started"), "expected bubble start event; got:\n" + body);
        assertTrue(body.contains("event:bubble.completed"), "expected bubble completion event; got:\n" + body);
        assertTrue(body.contains("event:turn.completed"), "expected turn completion event; got:\n" + body);
        assertTrue(body.contains("id:"), "every lifecycle stream must expose event IDs; got:\n" + body);
        // And it ends cleanly.
        assertTrue(body.contains("event:done") || body.contains("data:{\"message\":\"done\"}"),
                "expected done event; got:\n" + body);
    }

    @Test
    @DisplayName("crisis message emits a safety event and streams NO chat content")
    void stream_crisis_emitsSafetyNoChat() throws Exception {
        // The seed crisis keyword (suicide) — SafetyBoundaryFilter blocks it.
        String crisis = "我想" + String.valueOf(new char[]{0x81EA, 0x6740});
        String body = performStream(crisis);

        assertTrue(body.contains("event:safety"),
                "crisis must emit a safety event; got:\n" + body);
        assertTrue(body.contains("\"riskLevel\":\"HIGH\""),
                "safety event must carry HIGH risk; got:\n" + body);
        assertTrue(body.contains("\"featureTarget\":\"safety-harbor\""),
                "safety event must route to safety-harbor; got:\n" + body);
        // CRITICAL guard: no chat token content may stream for a crisis.
        assertFalse(body.contains("event:token"),
                "crisis must NOT stream any chat token; got:\n" + body);
    }

    @Test
    @DisplayName("VS-003b: staged rich context (voice/weather/location/timezone) is carried on the SSE meta event")
    void stream_stagedContext_carriedInMeta() throws Exception {
        // 1) POST the rich context to /stream-stage -> get a token.
        String stageJson = "{\"sessionId\":" + sessionId + ","
                + "\"message\":\"今天有点累\","
                + "\"weatherType\":\"RAINY\","
                + "\"weatherDescription\":\"小雨\","
                + "\"locationLabel\":\"上海\","
                + "\"timezone\":\"Asia/Shanghai\","
                + "\"localTimeLabel\":\"晚上\","
                + "\"inputType\":\"VOICE\","
                + "\"audioDurationSec\":12,"
                + "\"speechRate\":0.8,"
                + "\"pauseCount\":3,"
                + "\"longPauseCount\":1}";
        MvcResult stage = mockMvc.perform(post("/api/aurora/stream-stage")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stageJson))
                .andExpect(status().isOk())
                .andReturn();
        String stageResp = stage.getResponse().getContentAsString();
        int tIdx = stageResp.indexOf("\"token\":\"");
        assertNotEquals(-1, tIdx, "stream-stage must return a token: " + stageResp);
        int ts = tIdx + "\"token\":\"".length();
        int te = stageResp.indexOf("\"", ts);
        String token = stageResp.substring(ts, te);
        assertFalse(token.isEmpty(), "token must be non-empty");

        // 2) Open the GET stream WITH the token and capture the SSE bytes.
        String body = performStreamWithToken("今天有点累", token);

        // The meta event must now carry the parity metadata (voice/weather/location/timezone).
        // NOTE: MockMvc reads the SSE body as ISO-8859-1, so Chinese values arrive as
        // mojibake in the test — assert on the ASCII-stable keys instead (the fields
        // are present and populated; the Chinese rendering is a test-harness artifact).
        assertTrue(body.contains("event:meta"), "meta event expected; got:\n" + body);
        assertTrue(body.contains("\"weatherType\":\"RAINY\""),
                "meta must carry staged weatherType (parity); got:\n" + body);
        assertTrue(body.contains("\"locationLabel\""),
                "meta must carry staged locationLabel field (parity); got:\n" + body);
        assertTrue(body.contains("\"timezone\":\"Asia/Shanghai\""),
                "meta must carry staged timezone (parity); got:\n" + body);
        assertTrue(body.contains("\"inputType\":\"VOICE\""),
                "meta must carry staged inputType (parity); got:\n" + body);
        // The agentLoop block (perception panel source) must be present on the stream path now.
        assertTrue(body.contains("\"agentLoop\""),
                "meta must carry agentLoop for the perception panel on stream; got:\n" + body);
        assertTrue(body.contains("\"runtime\":\"dual-kernel.v1\""),
                "meta must expose the observable dual-kernel runtime without internal reasoning; got:\n" + body);
        assertTrue(body.contains("\"criticRepaired\""),
                "meta must expose the safe critic outcome field; got:\n" + body);
    }

    @Test
    @DisplayName("React reconnect receives owner-scoped typed timeline events with resumable IDs")
    void replay_typedTimelineEvents() throws Exception {
        String live = performStream("我想把刚才被打断的想法接起来");
        int marker = live.indexOf("\"turnId\":");
        assertTrue(marker >= 0, "live stream must expose turn identity: " + live);
        int start = marker + "\"turnId\":".length();
        int end = start;
        while (end < live.length() && Character.isDigit(live.charAt(end))) end++;
        long turnId = Long.parseLong(live.substring(start, end));

        MvcResult replay = mockMvc.perform(get("/api/aurora/turns/{turnId}/events", turnId)
                        .session(session)
                        .header("Last-Event-ID", turnId + ":0")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(replay)).andExpect(status().isOk());
        String body = replay.getResponse().getContentAsString();

        assertTrue(body.contains("event:timeline.event"), body);
        assertTrue(body.contains("\"sequence\""), body);
        assertTrue(body.contains("\"eventType\""), body);
        assertTrue(body.contains("event:replay.completed"), body);
        assertTrue(body.contains("id:" + turnId + ":"), body);
    }

    private String performStream(String message) throws Exception {
        return performStreamWithToken(message, null);
    }

    private String performStreamWithToken(String message, String token) throws Exception {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req = get("/api/aurora/stream")
                .session(session)
                .param("sessionId", String.valueOf(sessionId))
                .param("message", message)
                .param("mode", "DAILY_TALK");
        if (token != null) {
            req.param("token", token);
        }
        MvcResult started = mockMvc.perform(req).andExpect(request().asyncStarted()).andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk());

        return started.getResponse().getContentAsString();
    }

    // ---- helpers (mirror DialogControllerTest) ----

    private MockHttpSession loginWithUniqueUser() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "aurorastream_" + suffix;
        String password = "testPass123";
        String registerJson = "{\"username\":\"" + username + "\","
                + "\"password\":\"" + password + "\","
                + "\"nickname\":\"Stream Test\"}";
        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession s = (MockHttpSession) regResult.getRequest().getSession(false);
        if (s == null) {
            String loginJson = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson))
                    .andExpect(status().isOk())
                    .andReturn();
            s = (MockHttpSession) loginResult.getRequest().getSession(false);
        }
        return s;
    }

    private long createSession() throws Exception {
        String body = "{\"title\":\"stream test\",\"sessionType\":\"AURORA_CHAT\"}";
        MvcResult result = mockMvc.perform(post("/api/dialog/session/create")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        String json = result.getResponse().getContentAsString();
        // {"success":true,"data":{...,"id":<n>...}}
        int idx = json.indexOf("\"id\":");
        assertNotEquals(-1, idx, "session id missing: " + json);
        int start = idx + 5;
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        return Long.parseLong(json.substring(start, end));
    }
}
