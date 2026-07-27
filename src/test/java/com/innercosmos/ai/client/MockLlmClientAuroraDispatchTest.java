package com.innercosmos.ai.client;

import com.innercosmos.ai.prompt.StructuredOutputParser;
import com.innercosmos.ai.structured.StructuredAiResults.AuroraForegroundResult;
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
    void sixUserFacingModesAreBlindlyDistinguishableInOfflineDemo() {
        MockLlmClient client = new MockLlmClient(DIRECT_EXECUTOR);
        String sameInput = "这件事一直放在我心里，我还不知道该怎么面对。";
        java.util.Map<String, AuroraResult> results = new java.util.LinkedHashMap<>();
        for (String mode : java.util.List.of(
                "DAILY_TALK", "THOUGHT_CLARIFY", "SOCRATIC",
                "ACTION_SPLIT", "RELATION_REVIEW", "CAPSULE_SHAPING")) {
            results.put(mode, StructuredOutputParser.parse(
                    client.chat(request("AURORA_AGENT_LOOP_" + mode, sameInput)),
                    AuroraResult.class));
        }

        assertTrue(results.get("DAILY_TALK").segments.getFirst().contains("我在"));
        assertTrue(results.get("THOUGHT_CLARIFY").segments.getFirst().contains("事实"));
        assertTrue(results.get("SOCRATIC").segments.getFirst().contains("最强事实"));
        assertTrue(results.get("ACTION_SPLIT").segments.getFirst().contains("十分钟"));
        assertTrue(results.get("RELATION_REVIEW").segments.getFirst().contains("四层"));
        assertTrue(results.get("CAPSULE_SHAPING").segments.getFirst().contains("人格标签"));
        assertEquals(6, results.values().stream()
                .map(result -> String.join("", result.segments)).distinct().count());
    }

    @Test
    void mixedEmotionPresentationRequestIsGroundedAndActionable() {
        MockLlmClient client = new MockLlmClient(DIRECT_EXECUTOR);

        AuroraResult result = StructuredOutputParser.parse(
                client.chat(request("AURORA_AGENT_LOOP_DAILY_TALK",
                        "我明天要做项目展示，既兴奋又担心讲不清楚。先陪我稳一下，再帮我找到第一步。")),
                AuroraResult.class);

        assertNotNull(result);
        assertEquals(2, result.segments.size());
        assertTrue(result.segments.get(0).contains("兴奋和担心"), result.segments.get(0));
        assertTrue(result.segments.get(1).contains("老师"), result.segments.get(1));
        assertTrue(result.segments.get(1).contains("第一步"), result.segments.get(1));
        assertEquals("写下展示唯一需要被记住的一句话。", result.smallStep);
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
    void foregroundModule_usesItsOwnTextSchemaAndStaysGroundedInTheCurrentInput() {
        MockLlmClient client = new MockLlmClient(DIRECT_EXECUTOR);

        String raw = client.chat(request("AURORA_FOREGROUND_DAILY_TALK", "我们刚才还在讨论《驱魔人》的叙事视角"));
        AuroraForegroundResult result = StructuredOutputParser.parse(raw, AuroraForegroundResult.class);

        assertNotNull(result);
        assertNotNull(result.text, "foreground JSON must populate text rather than full-reply segments");
        assertTrue(result.text.contains("驱魔人"), result.text);
        assertFalse(result.text.contains("千与千寻"), result.text);
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
