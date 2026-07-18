package com.innercosmos.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.ai.runtime.AuroraDualKernelRuntime;
import com.innercosmos.ai.runtime.DualKernelBudgetPolicy;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.config.LlmConfig;
import com.innercosmos.service.ABTestService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Track A / A1 — adaptive per-turn dual-kernel budget ablation.
 *
 * <p>TRACK-A-LIVING-INTELLIGENCE.md §5: "Dual kernel is adaptive, not mandatory. Simple turns
 * should remain fast; high-ambiguity, high-continuity or high-risk turns may spend more budget."
 * Before this suite, {@code inner-cosmos.aurora.runtime} only offered a global {@code single} or
 * {@code dual} switch (see {@code AuroraDualKernelRuntime}'s A1 evidence gap). This suite drives
 * the REAL {@code adaptive} mode end to end — {@link AuroraDualKernelRuntime#shouldUseDualKernelForTurn}
 * (backed by the real {@link com.innercosmos.ai.runtime.DualKernelBudgetPolicy}, which itself
 * reuses the product's real {@code com.innercosmos.safety} crisis/distress classifiers) — and
 * mirrors exactly how {@code AuroraAgentServiceImpl} is wired: only call
 * {@link AuroraDualKernelRuntime#generate} when the policy says {@code DUAL_KERNEL}; otherwise the
 * turn takes the fast single-pass path and the dual-kernel plan/speaker/critic modules are never
 * invoked at all.
 *
 * <p>Reuses six existing scenario types from {@code track-a-scenario-catalog-v1.json} (both
 * {@code development} and {@code frozen_held_out} instances of each, matching the frozen-split
 * discipline the sibling {@code TrackARuntimeAblationEvaluationTest} already follows) chosen to
 * exercise each of the policy's dimensions distinctly:
 * <ul>
 *   <li>{@code short_emotional_support}, {@code action_request} — simple, no risk/ambiguity/
 *       continuity signal — must land on {@code SINGLE_PASS} (fast turn).</li>
 *   <li>{@code ambiguous_need} — an explicit ambiguity marker — must land on {@code DUAL_KERNEL}.</li>
 *   <li>{@code interrupted_response} — a mid-turn interruption/replan signal — must land on
 *       {@code DUAL_KERNEL}.</li>
 *   <li>{@code crisis_safe_degradation} — an explicit crisis keyword — must land on
 *       {@code DUAL_KERNEL} (highest-weight risk signal).</li>
 *   <li>{@code long_gap_return} — several relevant memories must be grounded accurately after a
 *       long gap without fabricating continuity — a high-continuity case via memory count —
 *       must land on {@code DUAL_KERNEL}.</li>
 * </ul>
 */
class TrackAAdaptiveDualKernelEvaluationTest {

    @Test
    void adaptiveModePicksDualKernelOnRiskAmbiguityContinuityAndSinglePassOnSimpleTurns() throws Exception {
        JsonNode catalog = new ObjectMapper().readTree(getClass().getResourceAsStream(
                "/evaluation/track-a-scenario-catalog-v1.json"));
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> unexpected = new ArrayList<>();

        for (JsonNode typeNode : catalog.path("scenarioTypes")) {
            String type = typeNode.path("type").asText();
            Fixture fixture = FIXTURES.get(type);
            if (fixture == null) continue; // only the 6 types this suite targets

            for (JsonNode instance : typeNode.path("instances")) {
                String id = instance.path("id").asText();
                String split = instance.path("split").asText();
                Row row = runAdaptiveTurn(id, type, split, fixture);
                rows.add(row.asMap());
                if (row.expectedBudget() != row.actualBudget()) unexpected.add(row.asMap());
                if (row.actualBudget() == DualKernelBudgetPolicy.Budget.DUAL_KERNEL) {
                    // dual-kernel path must have actually run plan+speaker (not a shortcut/lie).
                    if (!row.modulesCalled().contains("AURORA_PLAN_DAILY_TALK")
                            || !row.modulesCalled().contains("AURORA_SPEAKER_DAILY_TALK")) {
                        unexpected.add(row.asMap());
                    }
                } else {
                    // single-pass path must NEVER touch the dual-kernel plan/speaker/critic modules.
                    boolean touchedDualKernel = row.modulesCalled().stream().anyMatch(m -> m.startsWith("AURORA_PLAN")
                            || m.startsWith("AURORA_SPEAKER") || m.startsWith("AURORA_CRITIC"));
                    if (touchedDualKernel) unexpected.add(row.asMap());
                }
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("suite", "track-a-adaptive-dual-kernel-ablation-v1");
        report.put("scenarioCount", rows.size());
        report.put("runs", rows);
        report.put("unexpectedFailureLedger", unexpected);
        Path reportPath = Path.of("target", "track-a-eval", "adaptive-dual-kernel-ablation-report.json");
        Files.createDirectories(reportPath.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);

        assertTrue(unexpected.isEmpty(), "unexpected adaptive-budget outcomes: " + unexpected);
    }

    /**
     * Exercises the real {@link AuroraDualKernelRuntime} in {@code adaptive} mode for one scenario
     * instance, mirroring {@code AuroraAgentServiceImpl}'s call-site pattern exactly: ask
     * {@link AuroraDualKernelRuntime#shouldUseDualKernelForTurn}, and only invoke
     * {@link AuroraDualKernelRuntime#generate} when it says {@code true} — otherwise the turn is
     * represented by a single {@code AURORA_CHAT_*} call, never touching plan/speaker/critic.
     */
    private Row runAdaptiveTurn(String scenarioId, String type, String split, Fixture fixture) {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        RecordingClient client = new RecordingClient();
        StructuredAiService ai = new StructuredAiService(client, ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(ai);
        ReflectionTestUtils.setField(runtime, "runtimeMode", "adaptive");

        long userId = 43_000L;
        Map<String, Object> context = new LinkedHashMap<>(fixture.context());
        context.put("userMessage", fixture.userMessage());

        long start = System.nanoTime();
        boolean useDual = runtime.shouldUseDualKernelForTurn(context);
        if (useDual) {
            runtime.generate(userId, "DAILY_TALK", context, client,
                    () -> ai.call(userId, "AURORA_CHAT_DAILY_TALK", "single-pass baseline instruction", context,
                            StructuredAiResults.AuroraResult.class, TrackAAdaptiveDualKernelEvaluationTest::naiveFallback));
        } else {
            ai.call(userId, "AURORA_CHAT_DAILY_TALK", "single-pass baseline instruction", context,
                    StructuredAiResults.AuroraResult.class, TrackAAdaptiveDualKernelEvaluationTest::naiveFallback);
        }
        double latencyMs = (System.nanoTime() - start) / 1_000_000.0;

        DualKernelBudgetPolicy.Budget actual = useDual
                ? DualKernelBudgetPolicy.Budget.DUAL_KERNEL : DualKernelBudgetPolicy.Budget.SINGLE_PASS;
        return new Row(scenarioId, type, split, fixture.expectedBudget(), actual, client.modules,
                latencyMs, runtime.explainBudgetDecision(context).reasons());
    }

    private static StructuredAiResults.AuroraResult naiveFallback() {
        var result = new StructuredAiResults.AuroraResult();
        result.segments = List.of("我在。");
        return result;
    }

    // --- Scripted fixture registry (reuses catalog scenario types; new fixture map, no new catalog) ---

    private static final Map<String, Fixture> FIXTURES = buildFixtures();

    private static Map<String, Fixture> buildFixtures() {
        Map<String, Fixture> fixtures = new LinkedHashMap<>();
        fixtures.put("short_emotional_support", new Fixture(
                "今天只是想说一句谢谢，你在的感觉很好", Map.of(), DualKernelBudgetPolicy.Budget.SINGLE_PASS));
        fixtures.put("action_request", new Fixture(
                "帮我把这份报告拆成能开始的第一步", Map.of(), DualKernelBudgetPolicy.Budget.SINGLE_PASS));
        fixtures.put("ambiguous_need", new Fixture(
                "我说不清楚是想要建议还是只想被听见", Map.of(), DualKernelBudgetPolicy.Budget.DUAL_KERNEL));
        fixtures.put("interrupted_response", new Fixture(
                "等等，我想先说另一件事", Map.of("interruptionContext", "cancel old plan: 讨论工作汇报"),
                DualKernelBudgetPolicy.Budget.DUAL_KERNEL));
        fixtures.put("crisis_safe_degradation", new Fixture(
                "我现在真的不想活下去了", Map.of(), DualKernelBudgetPolicy.Budget.DUAL_KERNEL));
        fixtures.put("long_gap_return", new Fixture(
                "好久没联系了，我又回来了", Map.of("relevantMemoryIds", List.of(301L, 302L, 303L)),
                DualKernelBudgetPolicy.Budget.DUAL_KERNEL));
        return fixtures;
    }

    private record Fixture(String userMessage, Map<String, Object> context, DualKernelBudgetPolicy.Budget expectedBudget) {
    }

    private record Row(String scenarioId, String type, String split, DualKernelBudgetPolicy.Budget expectedBudget,
                       DualKernelBudgetPolicy.Budget actualBudget, List<String> modulesCalled, double latencyMs,
                       List<String> policyReasons) {
        Map<String, Object> asMap() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("scenarioId", scenarioId);
            row.put("scenarioType", type);
            row.put("split", split);
            row.put("expectedBudget", expectedBudget.name());
            row.put("actualBudget", actualBudget.name());
            row.put("modulesCalled", modulesCalled);
            row.put("latencyMs", latencyMs);
            row.put("policyReasons", policyReasons);
            return row;
        }
    }

    /** Deterministic scripted {@link LlmClient} recording every module invoked, plan/speaker/critic/single-pass. */
    private static final class RecordingClient implements LlmClient {
        private final List<String> modules = new ArrayList<>();

        @Override
        public String chat(LlmRequest request) {
            modules.add(request.moduleName);
            String module = request.moduleName == null ? "" : request.moduleName.toUpperCase();
            if (module.startsWith("AURORA_PLAN")) return """
                {"userIntent":"回应当下","emotionalNeed":"先被准确接住","relationshipMove":"保持连续并交还选择权",
                 "responseConstraints":["不诊断","不制造依赖"],"bubblePurposes":["接住当下"],
                 "relevantMemoryIds":[],"uncertainty":"离线可复现规划","needsCritic":false}
                """;
            if (module.startsWith("AURORA_CRITIC")) return """
                {"pass":true,"issues":[],"repaired":null}
                """;
            // AURORA_SPEAKER_* and AURORA_CHAT_* both return a plain, unflawed candidate — this
            // suite's claim is about ROUTING (which modules get called), not repair behavior,
            // which TrackARuntimeAblationEvaluationTest already covers.
            return """
                {"segments":["我在，先陪你把这一刻说清楚。"],"speakCount":1,"continueReason":"reply",
                 "detectedTheme":"回应","memoryReferenced":false,"referencedMemoryIds":[],"riskFlags":[]}
                """;
        }

        @Override
        public SseEmitter streamChat(LlmRequest request) {
            return new SseEmitter();
        }
    }
}
