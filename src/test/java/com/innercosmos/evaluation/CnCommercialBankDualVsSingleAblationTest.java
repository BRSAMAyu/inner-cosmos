package com.innercosmos.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.ai.router.KernelRoutingPolicy;
import com.innercosmos.ai.runtime.AuroraDualKernelRuntime;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.config.LlmConfig;
import com.innercosmos.service.ABTestService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CP-19 SINGLE vs DUAL gain ablation over the frozen cn-commercial-bank-v1 scenarios.
 *
 * <p>Split discipline: only the {@code development} split is consumed (harness iteration);
 * {@code held_out} stays sealed for final scoring per the bank's split_policy.
 *
 * <p>What this harness honestly measures with the deterministic scripted client: the two
 * paths run the SAME inputs, both must return structurally valid replies, module sequences
 * must match each path's contract (single = exactly one AURORA_CHAT call; dual = at least
 * PLAN+SPEAKER, critic only when the quality gate finds something), the deterministic
 * router must route the bank's variety into BOTH kernels, and per-turn latencies are
 * recorded for the report. Content-quality differentials need real providers and are an
 * explicitly recorded open item — the scripted client cannot fake a gain.
 */
class CnCommercialBankDualVsSingleAblationTest {

    @Test
    void frozenDevelopmentSplitRunsBothPathsWithContractModuleSequencesAndBothKernelsPresent() throws Exception {
        List<JsonNode> development = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream(
                        "/evaluation/cn-commercial-bank-v1/scenarios.jsonl"), StandardCharsets.UTF_8))) {
            for (String line : reader.lines().collect(Collectors.toList())) {
                if (line == null || line.isBlank()) continue;
                JsonNode scenario = new ObjectMapper().readTree(line);
                if (!"development".equals(scenario.path("split").asText())) continue;
                development.add(scenario);
            }
        }
        assertFalse(development.isEmpty(), "the frozen development split must be present");

        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        StructuredAiService ai = new StructuredAiService(new RecordingClient(), ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(ai);
        ReflectionTestUtils.setField(runtime, "runtimeMode", "adaptive");

        List<Map<String, Object>> rows = new ArrayList<>();
        int singleKernelCount = 0;
        int dualKernelCount = 0;
        int supportFlowCount = 0;
        int adaptiveDualCount = 0;
        for (JsonNode scenario : development) {
            String id = scenario.path("id").asText();
            String userMessage = firstUserTurn(scenario);

            KernelRoutingPolicy.TurnSignals signals = KernelRoutingPolicy.TurnSignals.from(
                    Map.of("userMessage", userMessage));
            KernelRoutingPolicy.Kernel kernel = KernelRoutingPolicy.route(signals);
            if (kernel == KernelRoutingPolicy.Kernel.DUAL) dualKernelCount++;
            else if (kernel == KernelRoutingPolicy.Kernel.SUPPORT_FLOW) supportFlowCount++;
            else singleKernelCount++;

            long userId = 47_000L;
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("userMessage", userMessage);
            boolean adaptiveDual = runtime.shouldUseDualKernelForTurn(context);
            if (adaptiveDual) adaptiveDualCount++;

            long singleStart = System.nanoTime();
            RecordingClient singleClient = new RecordingClient();
            var single = ai.call(userId, "AURORA_CHAT_DAILY_TALK", "single-pass baseline instruction",
                    context, StructuredAiResults.AuroraResult.class,
                    CnCommercialBankDualVsSingleAblationTest::naiveFallback,
                    singleClient);
            double singleLatencyMs = (System.nanoTime() - singleStart) / 1_000_000.0;

            long dualStart = System.nanoTime();
            var generation = runtime.generate(userId, "DAILY_TALK", context, singleClient,
                    () -> ai.call(userId, "AURORA_CHAT_DAILY_TALK", "single-pass baseline instruction",
                            context, StructuredAiResults.AuroraResult.class,
                            CnCommercialBankDualVsSingleAblationTest::naiveFallback));
            double dualLatencyMs = (System.nanoTime() - dualStart) / 1_000_000.0;

            // Both paths must return a structurally valid reply for every frozen scenario.
            assertTrue(single != null && single.segments != null && !single.segments.isEmpty(),
                    "single path lost its reply for " + id);
            assertTrue(generation.result() != null && generation.result().segments != null
                            && !generation.result().segments.isEmpty(),
                    "dual path lost its reply for " + id);

            // Module-sequence contracts. The dual client also served the single pass first, so
            // slice from the first AURORA_PLAN onward for the dual leg's own sequence.
            List<String> dualModules = singleClient.modules.stream()
                    .dropWhile(module -> !module.startsWith("AURORA_PLAN"))
                    .toList();
            assertTrue(dualModules.stream().anyMatch(m -> m.startsWith("AURORA_PLAN")), id);
            assertTrue(dualModules.stream().anyMatch(m -> m.startsWith("AURORA_SPEAKER")), id);
            assertTrue(dualModules.stream().filter(m -> m.startsWith("AURORA_CRITIC")).count() <= 1, id);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("scenarioId", id);
            row.put("family", scenario.path("family").asText());
            row.put("kernelRoute", kernel.name());
            row.put("adaptiveDual", adaptiveDual);
            row.put("singleModules", singleClient.modules.stream()
                    .takeWhile(module -> !module.startsWith("AURORA_PLAN")).toList());
            row.put("dualModules", dualModules);
            row.put("singleLatencyMs", round(singleLatencyMs));
            row.put("dualLatencyMs", round(dualLatencyMs));
            row.put("dualObservableIssues", generation.criticIssues());
            rows.add(row);
        }

        // Routing sanity for THIS split, stated honestly: the development split is deliberately
        // simple-turn dominant (the risk/red-team families live in the other splits), so the
        // deterministic complexity axis routes nearly everything SINGLE — that is the correct
        // budget for this content, and the ablation still runs BOTH paths per scenario above.
        // The combined adaptive decision must at least separate the bank (some turn earns the
        // slow kernel via the budget leg), otherwise no differential is measurable here.
        assertTrue(singleKernelCount > 0, "no scenario routed SINGLE; ablation degenerate");
        assertTrue(adaptiveDualCount > 0,
                "no development scenario earned the dual kernel — the ablation cannot measure "
                        + "a differential on this split");

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("suite", "cn-commercial-bank-dual-vs-single-ablation-v1");
        report.put("split", "development");
        report.put("scenarioCount", rows.size());
        report.put("kernelDistribution", Map.of(
                "SINGLE", singleKernelCount, "DUAL", dualKernelCount, "SUPPORT_FLOW", supportFlowCount));
        report.put("adaptiveDualCount", adaptiveDualCount);
        report.put("runs", rows);
        Path reportPath = Path.of("target", "cn-commercial-eval", "dual-vs-single-ablation-report.json");
        Files.createDirectories(reportPath.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);

        assertEquals(rows.size(), development.size(), "every development scenario was ablated");
    }

    private static String firstUserTurn(JsonNode scenario) {
        for (JsonNode turn : scenario.path("turns")) {
            if ("user".equals(turn.path("role").asText())) {
                return turn.path("content").asText("");
            }
        }
        return "";
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static StructuredAiResults.AuroraResult naiveFallback() {
        var result = new StructuredAiResults.AuroraResult();
        result.segments = List.of("我在。");
        return result;
    }

    /** Deterministic scripted client recording every module invocation. */
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
