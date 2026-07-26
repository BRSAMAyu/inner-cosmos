package com.innercosmos.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.ai.observability.AiTurnMetrics;
import com.innercosmos.ai.observability.AiTurnObservation;
import com.innercosmos.ai.runtime.AuroraDualKernelRuntime;
import com.innercosmos.ai.runtime.DualKernelBudgetPolicy;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.config.LlmConfig;
import com.innercosmos.service.ABTestService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.micrometer.observation.tck.TestObservationRegistryAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Offline, deterministic acceptance for the structural claim behind Aurora's adaptive dual kernel.
 *
 * <p>This deliberately gives forced-single and adaptive-dual the same input and the same visible
 * final reply. It therefore cannot "win" by authoring a nicer dual-kernel sentence. What it proves
 * is narrower and directly observable:
 *
 * <ul>
 *   <li>both variants receive the same raw perception/context signals;</li>
 *   <li>single pass makes one AURORA_CHAT call and has no separately inspectable plan/critic handoff;</li>
 *   <li>adaptive routes this ambiguity + interruption + continuity turn to dual kernel;</li>
 *   <li>dual kernel materialises a bounded plan, hands that exact plan to the speaker, and hands
 *       plan + candidate + deterministic issues to a critic before returning;</li>
 *   <li>the production metric/Observation types distinguish the two runtime labels without
 *       recording text, user IDs or memory IDs.</li>
 * </ul>
 *
 * <p>It does NOT prove that the dual reply is semantically better, that dual kernel acquires new
 * external facts, or that a proposed smallStep/featureTarget is executed as a tool action.
 */
class DualKernelInformationFlowEvaluationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long USER_ID = 48_001L;
    private static final String MODE = "DAILY_TALK";
    private static final String VISIBLE_REPLY = "先只做十分钟：把汇报第一页写成一句你真正想让大家记住的话。";
    private static final String SMALL_STEP = "用十分钟写出汇报第一页的一句核心结论";
    private static final String FEATURE_TARGET = "todo";

    @Test
    void sameInputProducesARealPlanSpeakerCriticHandoffWithoutClaimingAQualityWin() throws Exception {
        Map<String, Object> sameInput = richTurnContext();

        Variant single = runSingle(sameInput);
        Variant adaptiveDual = runAdaptive(sameInput);

        assertEquals(single.rawContext(), adaptiveDual.rawContext(),
                "both variants must start from the same assembled information");
        assertEquals(single.visibleSegments(), adaptiveDual.visibleSegments(),
                "the fixture holds visible output constant; this suite is not a quality preference test");
        assertEquals(List.of("AURORA_CHAT_DAILY_TALK"), single.modules());
        assertEquals(List.of(
                "AURORA_PLAN_DAILY_TALK",
                "AURORA_SPEAKER_DAILY_TALK",
                "AURORA_CRITIC_DAILY_TALK"), adaptiveDual.modules());

        assertEquals(DualKernelBudgetPolicy.Budget.DUAL_KERNEL, adaptiveDual.budget().budget());
        assertTrue(adaptiveDual.budget().reasons().contains("ambiguity:marker"));
        assertTrue(adaptiveDual.budget().reasons().contains("continuity:interruption"));
        assertTrue(adaptiveDual.budget().reasons().stream()
                .anyMatch(reason -> reason.startsWith("continuity:relevant_memories=")));

        JsonNode plan = adaptiveDual.stageContexts().get("AURORA_SPEAKER_DAILY_TALK")
                .path("responsePlan");
        assertEquals("只拆一个能开始的动作", plan.path("userIntent").asText());
        assertEquals("替用户做一个低后悔、十分钟内可开始的选择",
                plan.path("emotionalNeed").asText());
        assertEquals("接受打断，停止旧计划，只推进一个动作",
                plan.path("relationshipMove").asText());
        assertTrue(plan.path("responseConstraints").isArray());
        assertTrue(plan.path("bubblePurposes").isArray());
        assertEquals(List.of(701L, 702L), longValues(plan.path("relevantMemoryIds")));
        assertFalse(plan.path("uncertainty").asText().isBlank());
        assertTrue(plan.path("needsCritic").asBoolean());

        JsonNode critic = adaptiveDual.stageContexts().get("AURORA_CRITIC_DAILY_TALK");
        assertEquals(plan, critic.path("plan"), "critic must receive the exact speaker plan");
        assertEquals(VISIBLE_REPLY, critic.path("candidate").path("segments").get(0).asText());
        assertEquals(sameInput.get("userMessage"), critic.path("userInput").asText());
        assertTrue(critic.path("observableIssues").isArray());

        assertEquals("dual-kernel.v1", adaptiveDual.runtime());
        assertEquals("接受打断，停止旧计划，只推进一个动作",
                adaptiveDual.relationshipMove());
        assertEquals(SMALL_STEP, adaptiveDual.smallStep());
        assertEquals(FEATURE_TARGET, adaptiveDual.featureTarget());
        assertNotNull(adaptiveDual.stageLatenciesMs().get("plan"));
        assertNotNull(adaptiveDual.stageLatenciesMs().get("speaker"));
        assertNotNull(adaptiveDual.stageLatenciesMs().get("critic"));
        assertFalse(adaptiveDual.plannerFallbackUsed());
        assertFalse(adaptiveDual.speakerFallbackUsed());
        assertFalse(adaptiveDual.criticFallbackUsed());

        ObservabilityEvidence observability = verifyRuntimeObservability(single, adaptiveDual);
        writeReport(single, adaptiveDual, observability);
    }

    private Variant runSingle(Map<String, Object> sameInput) {
        RecordingClient client = new RecordingClient();
        StructuredAiService ai = structured(client);
        StructuredAiResults.AuroraResult result = ai.call(
                USER_ID, "AURORA_CHAT_" + MODE, "single-pass baseline", sameInput,
                StructuredAiResults.AuroraResult.class,
                DualKernelInformationFlowEvaluationTest::fallback, client);
        return new Variant(
                "forced-single",
                "single-pass.v1",
                null,
                List.copyOf(client.modules),
                Map.copyOf(client.contexts),
                client.contexts.get("AURORA_CHAT_DAILY_TALK"),
                List.copyOf(result.segments),
                result.smallStep,
                result.featureTarget,
                "",
                Map.of(),
                false,
                false,
                false);
    }

    private Variant runAdaptive(Map<String, Object> sameInput) {
        RecordingClient client = new RecordingClient();
        StructuredAiService ai = structured(client);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(ai);
        ReflectionTestUtils.setField(runtime, "runtimeMode", "adaptive");

        DualKernelBudgetPolicy.Decision decision = runtime.explainBudgetDecision(sameInput);
        assertTrue(runtime.shouldUseDualKernelForTurn(sameInput),
                "this deliberately rich turn must spend the dual-kernel budget");
        AuroraDualKernelRuntime.Generation generation = runtime.generate(
                USER_ID, MODE, sameInput, client,
                DualKernelInformationFlowEvaluationTest::fallback);

        StructuredAiResults.AuroraResult result = generation.result();
        return new Variant(
                "adaptive-selected-dual",
                generation.runtime(),
                decision,
                List.copyOf(client.modules),
                Map.copyOf(client.contexts),
                client.contexts.get("AURORA_PLAN_DAILY_TALK"),
                List.copyOf(result.segments),
                result.smallStep,
                result.featureTarget,
                generation.relationshipMove(),
                generation.stageLatenciesMs(),
                generation.plannerFallbackUsed(),
                generation.speakerFallbackUsed(),
                generation.criticFallbackUsed());
    }

    private static StructuredAiService structured(RecordingClient client) {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        return new StructuredAiService(client, ab, config);
    }

    private ObservabilityEvidence verifyRuntimeObservability(Variant single, Variant dual) {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        AiTurnMetrics metrics = new AiTurnMetrics(meters);
        metrics.recordTurn("chat", single.runtime(), "offline-scripted", MODE,
                false, false, 1);
        metrics.recordTurn("chat", dual.runtime(), "offline-scripted", MODE,
                false, false, 1);
        assertEquals(1.0, meters.find("aurora.turn.count")
                .tag("runtime", "single-pass.v1").counter().count());
        assertEquals(1.0, meters.find("aurora.turn.count")
                .tag("runtime", "dual-kernel.v1").counter().count());

        TestObservationRegistry singleRegistry = TestObservationRegistry.create();
        new AiTurnObservation(singleRegistry).record(
                "chat", single.runtime(), "offline-scripted", MODE, false, false, 1);
        assertThat(singleRegistry)
                .hasObservationWithNameEqualTo("aurora.turn").that()
                .hasLowCardinalityKeyValue("runtime", "single-pass.v1")
                .doesNotHaveLowCardinalityKeyValueWithKey("message")
                .doesNotHaveLowCardinalityKeyValueWithKey("userId");

        TestObservationRegistry dualRegistry = TestObservationRegistry.create();
        new AiTurnObservation(dualRegistry).record(
                "chat", dual.runtime(), "offline-scripted", MODE, false, false, 1);
        assertThat(dualRegistry)
                .hasObservationWithNameEqualTo("aurora.turn").that()
                .hasLowCardinalityKeyValue("runtime", "dual-kernel.v1")
                .doesNotHaveLowCardinalityKeyValueWithKey("message")
                .doesNotHaveLowCardinalityKeyValueWithKey("userId");

        return new ObservabilityEvidence(
                List.of("aurora.turn.count", "aurora.turn.latency"),
                "aurora.turn",
                "runtime",
                List.of("single-pass.v1", "dual-kernel.v1"));
    }

    private void writeReport(Variant single, Variant dual, ObservabilityEvidence observability)
            throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("suite", "dual-kernel-information-flow-v1");
        report.put("claim", "same raw context; dual adds an explicit plan/speaker/critic decision chain");
        report.put("notClaimed", List.of(
                "dual output is semantically better",
                "dual acquires external facts that single cannot access",
                "smallStep or featureTarget is automatically executed",
                "offline scripted provider represents real-provider quality"));
        report.put("sameVisibleOutputHeldConstant", true);
        report.put("single", single.asReportRow());
        report.put("adaptiveDual", dual.asReportRow());
        report.put("observability", observability.asMap());
        report.put("apiEvidence", Map.of(
                "providerTruth", "aiState.provider/model/mode/apiKeyConfigured/fallbackAllowed",
                "kernelTruth", "agentLoop.runtime/relationshipMove/critic*/stageLatenciesMs",
                "importantBoundary", "aiState alone does not prove that planner/speaker/critic ran"));
        report.put("remainingGaps", List.of(
                "production OTel has a runtime-tagged aurora.turn completion span but no per-stage planner/speaker/critic spans",
                "inner.cosmos.ai.provider identifies provider and mode, not individual kernel stage",
                "POST exposes agentLoop.stageLatenciesMs; current SSE meta omits that map",
                "action fields are proposals rendered to the user, not tool-execution receipts"));

        Path path = Path.of("target", "track-a-eval",
                "dual-kernel-information-flow-report.json");
        Files.createDirectories(path.getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), report);
    }

    private static Map<String, Object> richTurnContext() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("userMessage",
                "等等，先停掉刚才的方案。我说不清要安慰还是建议，但现在只替我选一个十分钟能开始的动作。");
        context.put("interruptionContext", "cancel previous three-step presentation plan");
        context.put("relevantMemoryIds", List.of(701L, 702L));
        context.put("memoryRecallAllowed", true);
        context.put("relationshipStageLabel", "FAMILIAR");
        context.put("currentStateSignal", "deadline_near; energy_low");
        context.put("userCalibration", Map.of(
                "auroraTone", "warm, candid and specific",
                "reflectionDepth", 4,
                "proactiveSensitivity", 4));
        context.put("foregroundAcknowledgementAlreadySent", true);
        return context;
    }

    private static StructuredAiResults.AuroraResult fallback() {
        StructuredAiResults.AuroraResult fallback = new StructuredAiResults.AuroraResult();
        fallback.segments = List.of("离线 fallback 不应在此验收中出现。");
        return fallback;
    }

    private static List<Long> longValues(JsonNode array) {
        List<Long> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asLong()));
        return values;
    }

    private static final class RecordingClient implements LlmClient {
        private final List<String> modules = new ArrayList<>();
        private final Map<String, JsonNode> contexts = new LinkedHashMap<>();

        @Override
        public String chat(LlmRequest request) {
            try {
                modules.add(request.moduleName);
                contexts.put(request.moduleName, JSON.readTree(request.requestJson));
            } catch (Exception exception) {
                throw new IllegalStateException("unable to inspect structured request", exception);
            }

            String module = request.moduleName == null ? "" : request.moduleName;
            if (module.startsWith("AURORA_PLAN_")) {
                return """
                    {"userIntent":"只拆一个能开始的动作",
                     "emotionalNeed":"替用户做一个低后悔、十分钟内可开始的选择",
                     "relationshipMove":"接受打断，停止旧计划，只推进一个动作",
                     "responseConstraints":["不重复旧计划","只给一个动作","不追问"],
                     "bubblePurposes":["确认新方向并给出唯一动作"],
                     "relevantMemoryIds":[701,702],
                     "uncertainty":"用户此刻更需要安慰还是建议仍不确定",
                     "needsCritic":true,"innerVoiceWorthy":false,"innerVoiceSeed":""}
                    """;
            }
            if (module.startsWith("AURORA_CRITIC_")) {
                return """
                    {"pass":true,"issues":[],"repaired":null}
                    """;
            }
            // Hold the visible answer and action proposal identical across forced-single and
            // adaptive-dual. Only the intermediate decision chain is under evaluation.
            return """
                {"segments":["%s"],"speakCount":1,"continueReason":"one bounded action",
                 "detectedTheme":"展示准备","nextQuestion":"","smallStep":"%s",
                 "featureSuggestion":"如果你愿意，可以把这一步留在待办里",
                 "featureTarget":"%s","memoryReferenced":true,
                 "referencedMemoryIds":[701,702],"riskFlags":[]}
                """.formatted(VISIBLE_REPLY, SMALL_STEP, FEATURE_TARGET);
        }

        @Override
        public SseEmitter streamChat(LlmRequest request) {
            return new SseEmitter();
        }
    }

    private record Variant(
            String variant,
            String runtime,
            DualKernelBudgetPolicy.Decision budget,
            List<String> modules,
            Map<String, JsonNode> stageContexts,
            JsonNode rawContext,
            List<String> visibleSegments,
            String smallStep,
            String featureTarget,
            String relationshipMove,
            Map<String, Long> stageLatenciesMs,
            boolean plannerFallbackUsed,
            boolean speakerFallbackUsed,
            boolean criticFallbackUsed) {

        Map<String, Object> asReportRow() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("variant", variant);
            row.put("runtime", runtime);
            row.put("modulesCalled", modules);
            row.put("moduleCount", modules.size());
            row.put("rawContextFieldCount", rawContext == null ? 0 : rawContext.size());
            row.put("explicitPlanPresent", stageContexts.containsKey("AURORA_SPEAKER_DAILY_TALK"));
            row.put("criticHandoffPresent", stageContexts.containsKey("AURORA_CRITIC_DAILY_TALK"));
            row.put("visibleActionProposal", Map.of(
                    "smallStep", smallStep == null ? "" : smallStep,
                    "featureTarget", featureTarget == null ? "" : featureTarget));
            row.put("relationshipMove", relationshipMove == null ? "" : relationshipMove);
            row.put("stageLatenciesMs", stageLatenciesMs);
            row.put("fallbacks", Map.of(
                    "planner", plannerFallbackUsed,
                    "speaker", speakerFallbackUsed,
                    "critic", criticFallbackUsed));
            if (budget != null) {
                row.put("adaptiveBudget", budget.budget().name());
                row.put("adaptiveScore", budget.score());
                row.put("adaptiveReasons", budget.reasons());
            }
            return row;
        }
    }

    private record ObservabilityEvidence(
            List<String> metricNames,
            String observationName,
            String distinguishingTag,
            List<String> observedRuntimeValues) {

        Map<String, Object> asMap() {
            return Map.of(
                    "metrics", metricNames,
                    "observation", observationName,
                    "distinguishingTag", distinguishingTag,
                    "observedRuntimeValues", observedRuntimeValues,
                    "containsSensitiveContent", false);
        }
    }
}
