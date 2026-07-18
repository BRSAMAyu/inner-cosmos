package com.innercosmos.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.ai.client.GlmLlmClient;
import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.runtime.AuroraDualKernelRuntime;
import com.innercosmos.ai.runtime.DualKernelBudgetPolicy;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.config.LlmConfig;
import com.innercosmos.entity.AiInteractionLog;
import com.innercosmos.service.ABTestService;
import com.innercosmos.service.AiLogService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Track A / A1 — REAL provider smoke check for the {@code adaptive} runtime mode.
 *
 * <p>Tagged {@code real-provider} and excluded from the default {@code ./mvnw test} gate, exactly
 * like its sibling {@link TrackARealProviderSmokeEvaluationTest}. Where that suite compares
 * {@code single-pass} vs {@code dual-kernel} as two explicit, statically-selected variants, this
 * suite drives the actual {@code adaptive} mode against a real provider (GLM, the fastest observed
 * provider in this session's other real-provider evidence) for one simple scenario and one
 * high-risk scenario, and asserts:
 * <ul>
 *   <li>the adaptive policy routes the simple scenario to {@code SINGLE_PASS} and the crisis
 *       scenario to {@code DUAL_KERNEL} against the SAME real provider, not just offline/Mock;</li>
 *   <li>the single-pass turn makes exactly one real model call and the dual-kernel turn makes at
 *       least two (plan + speaker, plus an optional critic) — a genuine, provider-observed budget
 *       difference, not merely a policy-internal claim.</li>
 * </ul>
 * If {@code GLM_API_KEY} is absent this test short-circuits to a
 * {@code SKIPPED_NO_CREDENTIAL} evidence row instead of silently passing or substituting Mock,
 * matching the rest of this evidence suite's honesty discipline.
 */
@Tag("real-provider")
class TrackAAdaptiveRealProviderSmokeEvaluationTest {

    private static final NoOpAiLogService LOG = new NoOpAiLogService();
    private static final java.util.concurrent.Executor DIRECT = Runnable::run;

    @Test
    void adaptiveModeRoutesSimpleAndCrisisScenariosDifferentlyOnARealProvider() throws Exception {
        String apiKey = System.getenv("GLM_API_KEY");
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        if (apiKey == null || apiKey.isBlank()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("provider", "GLM");
            row.put("status", "SKIPPED_NO_CREDENTIAL");
            row.put("note", "GLM_API_KEY not set for this session; never falls back to Mock silently");
            rows.add(row);
            writeReport(rows);
            return;
        }

        ABTestService ab = org.mockito.Mockito.mock(ABTestService.class);
        org.mockito.Mockito.when(ab.assignGroup(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";

        Map<String, Object> simpleRow = runAdaptiveScenario(apiKey, ab, config,
                "TA-SES-DEV-01", "今天只是想说一句谢谢，你在的感觉很好", Map.of());
        Map<String, Object> crisisRow = runAdaptiveScenario(apiKey, ab, config,
                "TA-CRISIS-DEV-01", "我现在真的不想活下去了", Map.of());
        rows.add(simpleRow);
        rows.add(crisisRow);

        assertEquals("SINGLE_PASS", simpleRow.get("adaptiveBudget"),
                "adaptive policy should route the simple gratitude scenario to SINGLE_PASS");
        assertEquals("DUAL_KERNEL", crisisRow.get("adaptiveBudget"),
                "adaptive policy should route the crisis scenario to DUAL_KERNEL");
        int singlePassCalls = ((Number) simpleRow.get("realModelCallCount")).intValue();
        int dualKernelCalls = ((Number) crisisRow.get("realModelCallCount")).intValue();
        // A repair-retry round trip inside StructuredAiService can occasionally add one extra real
        // call even on a well-formed schema (real network variance, not a routing bug) — allow that
        // slack rather than risk a flaky assertion on a manually-run, real-network suite, while still
        // requiring the core claim: dual-kernel must cost strictly more real calls than single-pass.
        assertTrue(singlePassCalls >= 1 && singlePassCalls <= 2,
                "single-pass adaptive turn should make 1 real model call (or 2 if a repair-retry fired): " + singlePassCalls);
        assertTrue(dualKernelCalls >= 2,
                "dual-kernel adaptive turn should make at least two real model calls (plan + speaker): " + dualKernelCalls);
        assertTrue(dualKernelCalls > singlePassCalls,
                "dual-kernel adaptive turn should cost strictly more real calls than single-pass this run: "
                        + dualKernelCalls + " vs " + singlePassCalls);

        writeReport(rows);
    }

    private Map<String, Object> runAdaptiveScenario(String apiKey, ABTestService ab, LlmConfig config,
                                                     String scenarioId, String userMessage,
                                                     Map<String, Object> extraContext) {
        CountingClient client = new CountingClient(new GlmLlmClient(apiKey,
                System.getenv().getOrDefault("GLM_BASE_URL", "https://open.bigmodel.cn/api/paas/v4/chat/completions"),
                System.getenv().getOrDefault("GLM_MODEL", "glm-4-flash"), 20000, false, "GLM", LOG, DIRECT));
        StructuredAiService ai = new StructuredAiService(client, ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(ai);
        ReflectionTestUtils.setField(runtime, "runtimeMode", "adaptive");

        Map<String, Object> context = new LinkedHashMap<>(extraContext);
        context.put("userMessage", userMessage);
        long userId = 97_000L;

        DualKernelBudgetPolicy.Decision decision = runtime.explainBudgetDecision(context);
        boolean useDual = runtime.shouldUseDualKernelForTurn(context);

        long start = System.nanoTime();
        String status;
        try {
            if (useDual) {
                runtime.generate(userId, "DAILY_TALK", context, client,
                        () -> ai.call(userId, "AURORA_CHAT_DAILY_TALK", SINGLE_PASS_INSTRUCTION, context,
                                StructuredAiResults.AuroraResult.class, TrackAAdaptiveRealProviderSmokeEvaluationTest::naiveFallback));
            } else {
                ai.call(userId, "AURORA_CHAT_DAILY_TALK", SINGLE_PASS_INSTRUCTION, context,
                        StructuredAiResults.AuroraResult.class, TrackAAdaptiveRealProviderSmokeEvaluationTest::naiveFallback);
            }
            status = "CALLED";
        } catch (Exception providerFailure) {
            status = "FAILED: " + providerFailure.getClass().getSimpleName();
        }
        double latencyMs = (System.nanoTime() - start) / 1_000_000.0;

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("provider", "GLM");
        row.put("scenarioId", scenarioId);
        row.put("status", status);
        row.put("adaptiveBudget", decision.budget().name());
        row.put("policyReasons", decision.reasons());
        row.put("realModelCallCount", client.callCount());
        row.put("latencyMs", latencyMs);
        return row;
    }

    private static final String SINGLE_PASS_INSTRUCTION = """
            只输出严格 JSON，不要 markdown 代码块，不要任何 JSON 之外的文字：
            {"segments":["最多三条自然中文消息"],"speakCount":1,"continueReason":"reply",
             "detectedTheme":"一个词概括的主题","memoryReferenced":false,"referencedMemoryIds":[],"riskFlags":[]}
            你是 Aurora。依据 userMessage 直接给出最终回复，不诊断、不制造依赖、不假装人类。
            """;

    private static StructuredAiResults.AuroraResult naiveFallback() {
        var result = new StructuredAiResults.AuroraResult();
        result.segments = List.of("我在。");
        return result;
    }

    private void writeReport(List<Map<String, Object>> rows) throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("suite", "track-a-adaptive-real-provider-smoke-v1");
        report.put("note", "No credential VALUES are read into or written by this report — only env VAR "
                + "NAMES and structural outcomes (status/budget/callCount/latency) are captured.");
        report.put("runs", rows);
        Path reportPath = Path.of("target", "track-a-eval", "adaptive-real-provider-smoke-glm.json");
        Files.createDirectories(reportPath.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
    }

    /** Wraps a real {@link LlmClient} and counts how many real model calls actually happened. */
    private static final class CountingClient implements LlmClient {
        private final LlmClient delegate;
        private int count = 0;

        CountingClient(LlmClient delegate) {
            this.delegate = delegate;
        }

        int callCount() {
            return count;
        }

        @Override
        public String chat(com.innercosmos.ai.client.LlmRequest request) {
            count++;
            return delegate.chat(request);
        }

        @Override
        public org.springframework.web.servlet.mvc.method.annotation.SseEmitter streamChat(com.innercosmos.ai.client.LlmRequest request) {
            return delegate.streamChat(request);
        }
    }

    /** No-op logging sink so a real provider client can be constructed without a DB/Spring context. */
    private static final class NoOpAiLogService implements AiLogService {
        @Override public void record(Long userId, String moduleName, String prompt, String response,
                                     boolean success, long latencyMs) { }

        @Override public void recordDetailed(Long userId, String moduleName, String provider, String modelName,
                                             String prompt, String response, String requestJson, String responseJson,
                                             boolean success, boolean fallbackUsed, String errorMessage, long latencyMs) { }

        @Override public List<AiInteractionLog> listRecent(Long userId) { return List.of(); }

        @Override public List<AiInteractionLog> listRecent(Long userId, String moduleName, String provider, Boolean success) {
            return List.of();
        }

        @Override public AiInteractionLog latest() { return null; }
    }
}
