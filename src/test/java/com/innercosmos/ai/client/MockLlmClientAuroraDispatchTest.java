package com.innercosmos.ai.client;

import com.innercosmos.ai.prompt.StructuredOutputParser;
import com.innercosmos.ai.structured.StructuredAiResults.AuroraResult;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for the Aurora structured-output dispatch bug.
 *
 * <p>The real Aurora service calls {@code StructuredAiService} with module names
 * {@code "AURORA_AGENT_LOOP_<mode>"} (normal chat reply) and
 * {@code "AURORA_PROACTIVE_GREETING_<mode>"} (proactive greeting). Previously
 * {@link MockLlmClient} only emitted structured JSON for modules containing the
 * literal substrings {@code "AURORA_CHAT"} / {@code "AURORA_GREETING"}, so the real
 * module names fell through to plain text → parser returned null → the service used
 * the static fallback AuroraResult for every demo chat turn and greeting.
 *
 * <p>These tests drive the real {@link MockLlmClient} with the real module names and
 * assert the produced string parses into a non-fallback {@link AuroraResult} whose
 * content varies by input.
 */
class MockLlmClientAuroraDispatchTest {

    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    private LlmRequest request(String moduleName, String userText) {
        // Mirrors how StructuredAiService constructs the request: the user text is part of
        // the context JSON which is embedded into both prompt and requestJson.
        String contextJson = "{\"userMessage\":\"" + userText + "\",\"mode\":\"DAILY_TALK\"}";
        String prompt = "You are an Inner Cosmos structured reasoning worker.\nInput JSON:\n" + contextJson;
        LlmRequest req = new LlmRequest(1L, moduleName, prompt);
        req.requestJson = contextJson;
        return req;
    }

    @Test
    void agentLoopModule_negativeSentiment_parsesToNonFallbackAuroraResult() {
        MockLlmClient client = new MockLlmClient(DIRECT_EXECUTOR);

        String raw = client.chat(request("AURORA_AGENT_LOOP_DAILY_TALK", "我最近真的好累好焦虑，每天都很烦，快崩了"));

        // Must be structured JSON, not plain text.
        assertNotNull(raw);
        assertTrue(raw.trim().startsWith("{"), "Agent-loop module must yield JSON, got: " + raw);

        AuroraResult result = StructuredOutputParser.parse(raw, AuroraResult.class);
        assertNotNull(result, "AURORA_AGENT_LOOP_* output must parse into AuroraResult (no fallback)");
        assertFalse(result.segments.isEmpty(), "Parsed result must contain reply segments");
        // A successful parse means StructuredAiService never reaches the fallback path,
        // so the static fallback's FALLBACK_USED risk flag is never present.
        assertFalse(result.riskFlags.contains("FALLBACK_USED"),
                "Parsed (non-fallback) result must not carry FALLBACK_USED");
        assertNotNull(result.detectedTheme);
        assertNotNull(result.nextQuestion);
    }

    @Test
    void agentLoopModule_variesByInput() {
        MockLlmClient client = new MockLlmClient(DIRECT_EXECUTOR);

        AuroraResult task = StructuredOutputParser.parse(
                client.chat(request("AURORA_AGENT_LOOP_DAILY_TALK", "这个任务太大了我一直拖延工作做不完")),
                AuroraResult.class);
        AuroraResult relation = StructuredOutputParser.parse(
                client.chat(request("AURORA_AGENT_LOOP_DAILY_TALK", "我和朋友家人同事的关系最近很紧张")),
                AuroraResult.class);

        assertNotNull(task);
        assertNotNull(relation);
        // Input-varying behaviour: task-stress and relation inputs produce different next questions.
        assertNotEquals(task.nextQuestion, relation.nextQuestion,
                "Mock should produce input-dependent nextQuestion (keyword/intent aware)");
    }

    @Test
    void proactiveGreetingModule_stillProducesGreetingJson() {
        MockLlmClient client = new MockLlmClient(DIRECT_EXECUTOR);

        String raw = client.chat(request("AURORA_PROACTIVE_GREETING_DAILY_TALK", "请 Aurora 主动发起对话"));

        assertNotNull(raw);
        assertTrue(raw.trim().startsWith("{"), "Greeting module must yield JSON, got: " + raw);

        AuroraResult result = StructuredOutputParser.parse(raw, AuroraResult.class);
        assertNotNull(result, "AURORA_PROACTIVE_GREETING_* output must parse into AuroraResult");
        assertFalse(result.segments.isEmpty(), "Greeting must contain segments");
        assertFalse(result.riskFlags.contains("FALLBACK_USED"));
    }

    @Test
    void legacyAuroraChatAndGreetingModules_stillWork() {
        MockLlmClient client = new MockLlmClient(DIRECT_EXECUTOR);

        AuroraResult chat = StructuredOutputParser.parse(
                client.chat(request("AURORA_CHAT", "今天心情还行")), AuroraResult.class);
        AuroraResult greeting = StructuredOutputParser.parse(
                client.chat(request("AURORA_GREETING", "你好")), AuroraResult.class);

        assertNotNull(chat, "Legacy AURORA_CHAT must still parse (back-compat)");
        assertNotNull(greeting, "Legacy AURORA_GREETING must still parse (back-compat)");
        assertFalse(chat.segments.isEmpty());
        assertFalse(greeting.segments.isEmpty());
    }
}
