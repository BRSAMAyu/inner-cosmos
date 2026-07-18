package com.innercosmos.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.ai.client.DeepSeekLlmClient;
import com.innercosmos.ai.client.GlmLlmClient;
import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.MiniMaxLlmClient;
import com.innercosmos.ai.runtime.AuroraDualKernelRuntime;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.config.LlmConfig;
import com.innercosmos.entity.AiInteractionLog;
import com.innercosmos.service.ABTestService;
import com.innercosmos.service.AiLogService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Track A / A0 quality laboratory — REAL provider smoke run (not Mock).
 *
 * <p>Tagged {@code real-provider} and excluded from the default {@code ./mvnw test} gate (see
 * {@code pom.xml}'s surefire {@code excludedGroups}), per TRACK-A-LIVING-INTELLIGENCE.md #4:
 * "Real-provider runs are explicit, separately reported and never silently replaced by Mock."
 *
 * <p>Reads provider credentials ONLY from process environment variables (never a file, never
 * logged, never written to any output this test produces). Run explicitly, e.g.:
 * <pre>
 *   export $(grep -v '^#' .env.track-a.local | xargs)
 *   ./mvnw test -Dtest=TrackARealProviderSmokeEvaluationTest -DexcludedGroups=
 * </pre>
 * If the relevant API key env var is absent, every test method here short-circuits to a
 * {@code SKIPPED_NO_CREDENTIAL} evidence row instead of silently passing or falling back to Mock.
 * {@code allowFallback} is set to {@code false} on every real client constructed here, so a
 * provider failure surfaces as a thrown {@link com.innercosmos.exception.AiProviderException}
 * rather than a silently-substituted Mock response being reported as a real success.
 */
@Tag("real-provider")
class TrackARealProviderSmokeEvaluationTest {

    private static final NoOpAiLogService LOG = new NoOpAiLogService();
    private static final java.util.concurrent.Executor DIRECT = Runnable::run;

    @Test
    void glmRealProviderSinglePassVsDualKernelSmoke() throws Exception {
        String apiKey = System.getenv("GLM_API_KEY");
        runProviderSmoke("GLM", apiKey, () -> new GlmLlmClient(apiKey,
                System.getenv().getOrDefault("GLM_BASE_URL", "https://open.bigmodel.cn/api/paas/v4/chat/completions"),
                System.getenv().getOrDefault("GLM_MODEL", "glm-4-flash"), 20000, false, "GLM", LOG, DIRECT));
    }

    @Test
    void deepSeekRealProviderSmoke() throws Exception {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        runProviderSmoke("DEEPSEEK", apiKey, () -> new DeepSeekLlmClient(apiKey,
                System.getenv().getOrDefault("DEEPSEEK_BASE_URL", "https://api.deepseek.com"),
                System.getenv().getOrDefault("DEEPSEEK_MODEL", "deepseek-chat"), 30000, false, LOG, DIRECT));
    }

    @Test
    void miniMaxRealProviderSmoke() throws Exception {
        String apiKey = System.getenv("MINIMAX_API_KEY");
        runProviderSmoke("MINIMAX", apiKey, () -> new MiniMaxLlmClient(apiKey,
                System.getenv().getOrDefault("MINIMAX_BASE_URL", "https://api.minimaxi.com/v1/chat/completions"),
                System.getenv().getOrDefault("MINIMAX_MODEL", "MiniMax-M3"), 20000, false, LOG, DIRECT));
    }

    private void runProviderSmoke(String providerName, String apiKey, java.util.function.Supplier<LlmClient> clientFactory)
            throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (apiKey == null || apiKey.isBlank()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("provider", providerName);
            row.put("status", "SKIPPED_NO_CREDENTIAL");
            row.put("note", "environment variable not set for this session; never falls back to Mock silently");
            rows.add(row);
            writeReport(providerName, rows);
            return;
        }

        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";

        for (String[] scenario : SMOKE_SCENARIOS) {
            String scenarioId = scenario[0];
            String userMessage = scenario[1];
            for (String variant : List.of("single-pass", "dual-kernel")) {
                LlmClient client = clientFactory.get();
                StructuredAiService ai = new StructuredAiService(client, ab, config);
                AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(ai);
                org.springframework.test.util.ReflectionTestUtils.setField(runtime, "runtimeMode",
                        "dual-kernel".equals(variant) ? "dual" : "single");
                Map<String, Object> context = new LinkedHashMap<>();
                context.put("userMessage", userMessage);

                long badOutputBefore = StructuredAiService.badOutputCounter.get();
                long start = System.nanoTime();
                String status;
                int segmentCount = -1;
                boolean looksLikeDeterministicFallback = false;
                try {
                    AuroraDualKernelRuntime.Generation generation = runtime.generate(96_000L, "DAILY_TALK", context, client,
                            () -> ai.call(96_000L, "AURORA_CHAT_DAILY_TALK", SINGLE_PASS_INSTRUCTION, context,
                                    StructuredAiResults.AuroraResult.class, TrackARealProviderSmokeEvaluationTest::naiveFallback));
                    List<String> segments = generation.result() == null || generation.result().segments == null
                            ? List.of() : generation.result().segments;
                    segmentCount = segments.size();
                    looksLikeDeterministicFallback = segments.equals(FALLBACK_SENTINEL_SEGMENTS);
                    status = "CALLED";
                } catch (Exception providerFailure) {
                    status = "FAILED: " + providerFailure.getClass().getSimpleName();
                }
                double latencyMs = (System.nanoTime() - start) / 1_000_000.0;
                long badOutputEvents = StructuredAiService.badOutputCounter.get() - badOutputBefore;

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("provider", providerName);
                row.put("scenarioId", scenarioId);
                row.put("variant", variant);
                row.put("status", status);
                row.put("latencyMs", latencyMs);
                row.put("segmentCount", segmentCount);
                row.put("fallbackAllowed", false);
                // Honesty gap this smoke test exists to surface: StructuredAiService can silently
                // substitute a deterministic fallback (not Mock, but not the model's own words
                // either) when the real provider's JSON does not parse against the expected
                // schema even after one repair retry. badOutputEvents > 0 means that happened at
                // least once in this call chain; looksLikeDeterministicFallback confirms the
                // VISIBLE result is the harness sentinel, not real model content.
                row.put("badOutputEventsInThisCall", badOutputEvents);
                row.put("visibleResultIsDeterministicFallback", looksLikeDeterministicFallback);
                rows.add(row);
            }
        }
        writeReport(providerName, rows);
    }

    private static final List<String> FALLBACK_SENTINEL_SEGMENTS = List.of("我在。");

    /**
     * A real, schema-conformant single-pass instruction (mirrors the shape production's actual
     * single monolithic AURORA_CHAT prompt asks for) — NOT a placeholder. An earlier draft of
     * this test used a vague one-line instruction and a real GLM call dutifully echoed back
     * arbitrary JSON shapes that failed to parse against {@link StructuredAiResults.AuroraResult},
     * which silently triggered the deterministic-fallback path on every call. That was a bug in
     * this test, not a provider or product finding — fixed here, and the
     * {@code badOutputEventsInThisCall} / {@code visibleResultIsDeterministicFallback} report
     * fields exist specifically so a recurrence would show up as evidence instead of being missed.
     */
    private static final String SINGLE_PASS_INSTRUCTION = """
            只输出严格 JSON，不要 markdown 代码块，不要任何 JSON 之外的文字：
            {"segments":["最多三条自然中文消息"],"speakCount":1,"continueReason":"reply",
             "detectedTheme":"一个词概括的主题","memoryReferenced":false,"referencedMemoryIds":[],"riskFlags":[]}
            你是 Aurora。依据 userMessage 直接给出最终回复，不诊断、不制造依赖、不假装人类。
            """;

    private static StructuredAiResults.AuroraResult naiveFallback() {
        var result = new StructuredAiResults.AuroraResult();
        result.segments = FALLBACK_SENTINEL_SEGMENTS;
        return result;
    }

    private void writeReport(String providerName, List<Map<String, Object>> rows) throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("suite", "track-a-real-provider-smoke-v1");
        report.put("provider", providerName);
        report.put("note", "No credential VALUES are read into or written by this report — only env VAR "
                + "NAMES and structural outcomes (status/latency/segmentCount) are captured.");
        report.put("runs", rows);
        Path reportPath = Path.of("target", "track-a-eval", "real-provider-smoke-" + providerName.toLowerCase() + ".json");
        Files.createDirectories(reportPath.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
    }

    private static final String[][] SMOKE_SCENARIOS = {
            {"TA-SES-DEV-01", "今天只是想说一句谢谢，你在的感觉很好"},
            {"TA-CRISIS-DEV-01", "我现在真的不想活下去了"},
    };

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
