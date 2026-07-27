package com.innercosmos.ai.runtime;

import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.config.LlmConfig;
import com.innercosmos.service.ABTestService;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuroraPlannerLifecycleTest {

    @Test
    void invalidPlannerJsonIsFallbackEvidenceAndNeverBecomesRealGuidance() throws Exception {
        ScriptedClient client = new ScriptedClient(PlannerBehavior.INVALID_JSON);
        try (Harness harness = harness(client)) {
            AuroraDualKernelRuntime.Generation first = harness.runtime.generate(7L, "DAILY_TALK",
                    context("第一轮"), client, StructuredAiResults.AuroraResult::new);

            assertThat(first.backgroundPlannerStatus()).isEqualTo("SCHEDULED");
            assertThat(first.backgroundPlannerEvidence().get(2, TimeUnit.SECONDS).status())
                    .isEqualTo(AuroraDualKernelRuntime.PlannerStatus.FALLBACK);

            AuroraDualKernelRuntime.Generation second = harness.runtime.generate(7L, "DAILY_TALK",
                    context("第二轮"), client, StructuredAiResults.AuroraResult::new);
            assertThat(second.guidanceSource()).isEqualTo("fallback");
            assertThat(second.plannerFallbackUsed()).isTrue();
            assertThat(client.latestSpeakerRequestJson).contains("guidanceUnavailable", "fallback")
                    .doesNotContain("dialogueGuidance");
        }
    }

    @Test
    void providerLengthFailureIsFailedEvidenceAndNeverBlocksSpeaker() throws Exception {
        ScriptedClient client = new ScriptedClient(PlannerBehavior.LENGTH_FAILURE);
        try (Harness harness = harness(client)) {
            long started = System.nanoTime();
            AuroraDualKernelRuntime.Generation first = harness.runtime.generate(7L, "DAILY_TALK",
                    context("第一轮"), client, StructuredAiResults.AuroraResult::new);
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(500);

            AuroraDualKernelRuntime.PlannerRunEvidence evidence =
                    first.backgroundPlannerEvidence().get(2, TimeUnit.SECONDS);
            assertThat(evidence.status()).isEqualTo(AuroraDualKernelRuntime.PlannerStatus.FAILED);
            assertThat(evidence.detail()).contains("finish_reason=length");

            AuroraDualKernelRuntime.Generation second = harness.runtime.generate(7L, "DAILY_TALK",
                    context("第二轮"), client, StructuredAiResults.AuroraResult::new);
            assertThat(second.guidanceSource()).isEqualTo("failed");
            assertThat(second.relationshipMove()).isEmpty();
        }
    }

    @Test
    void saturatedPlannerExecutorReturnsSpeakerAndPublishesFailedEvidence() throws Exception {
        ScriptedClient client = new ScriptedClient(PlannerBehavior.INVALID_JSON);
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(
                new StructuredAiService(client, ab, config));
        runtime.setPlannerExecutor(command -> {
            throw new java.util.concurrent.RejectedExecutionException("saturated");
        });
        runtime.setDeliberationExecution("legacy-next-turn");

        AuroraDualKernelRuntime.Generation generation = runtime.generate(7L, "DAILY_TALK",
                context("池满时也要先回复"), client, StructuredAiResults.AuroraResult::new);

        assertThat(generation.result().segments).containsExactly("我听见了。");
        assertThat(generation.backgroundPlannerEvidence().get(100, TimeUnit.MILLISECONDS).status())
                .isEqualTo(AuroraDualKernelRuntime.PlannerStatus.FAILED);
        assertThat(generation.backgroundPlannerEvidence().get().detail())
                .isEqualTo("planner_executor_saturated");
    }
    private static Map<String, Object> context(String message) {
        return Map.of("sessionId", 991L, "userMessage", message,
                "agentLoopPolicy", "按语境选择数量");
    }

    private static Harness harness(LlmClient client) {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        StructuredAiService structured = new StructuredAiService(client, ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(structured);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        runtime.setPlannerExecutor(pool);
        runtime.setDeliberationExecution("legacy-next-turn");
        return new Harness(runtime, pool);
    }

    private enum PlannerBehavior { INVALID_JSON, LENGTH_FAILURE }

    private static final class ScriptedClient implements LlmClient {
        private final PlannerBehavior behavior;
        private volatile String latestSpeakerRequestJson = "";

        private ScriptedClient(PlannerBehavior behavior) {
            this.behavior = behavior;
        }

        @Override
        public String chat(LlmRequest request) {
            if (request.moduleName.startsWith("AURORA_SPEAKER")) {
                latestSpeakerRequestJson = request.requestJson;
                return """
                        {"segments":["我听见了。"],"speakCount":1,"continueReason":"接住",
                         "detectedTheme":"","nextQuestion":"","smallStep":"",
                         "featureSuggestion":"","featureTarget":"","memoryReferenced":false,
                         "referencedMemoryIds":[],"riskFlags":[]}
                        """;
            }
            if (request.moduleName.startsWith("AURORA_PLAN")) {
                if (behavior == PlannerBehavior.LENGTH_FAILURE) {
                    throw new RuntimeException("finish_reason=length, reasoning_content_present=true");
                }
                return "not-json";
            }
            return "still-not-json";
        }

        @Override
        public SseEmitter streamChat(LlmRequest request) {
            return new SseEmitter();
        }
    }

    private record Harness(AuroraDualKernelRuntime runtime, ExecutorService pool)
            implements AutoCloseable {
        @Override
        public void close() {
            pool.shutdownNow();
        }
    }
}
