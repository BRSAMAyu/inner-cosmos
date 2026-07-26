package com.innercosmos.ai.runtime;

import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.config.LlmConfig;
import com.innercosmos.service.ABTestService;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuroraPipelinedDualKernelRuntimeTest {

    @Test
    void blockedBackgroundPlannerDoesNotDelaySpeakerAndItsGuidanceFeedsTheNextTurn() throws Exception {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        BlockingPlannerClient client = new BlockingPlannerClient();
        StructuredAiService structured = new StructuredAiService(client, ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(structured);
        ExecutorService plannerPool = Executors.newSingleThreadExecutor();
        runtime.setPlannerExecutor(plannerPool);

        Map<String, Object> firstContext = Map.of(
                "sessionId", 91L,
                "userMessage", "午饭还不错",
                "agentLoopPolicy", "按语境选择数量");

        AuroraDualKernelRuntime.Generation first = assertTimeoutPreemptively(
                Duration.ofSeconds(1),
                () -> runtime.generate(7L, "DAILY_TALK", firstContext, client,
                        StructuredAiResults.AuroraResult::new));

        assertThat(first.result().segments).containsExactly("午饭这次选对了。");
        assertThat(first.runtime()).isEqualTo("dual-kernel.pipeline.v2");
        assertThat(first.backgroundPlannerScheduled()).isTrue();
        assertThat(first.guidanceSource()).isEqualTo("bootstrap");
        assertThat(first.backgroundPlannerStatus()).isEqualTo("SCHEDULED");
        assertThat(client.plannerStarted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(client.latestSpeakerSystemPrompt)
                .contains("没有默认数量", "不要把 1 条当作最安全答案", "禁止随机抽数");
        assertThat(first.deferredInnerVoiceRequest()).isNull();

        client.releasePlanner.countDown();
        assertThat(first.backgroundPlannerEvidence().get(2, TimeUnit.SECONDS).status())
                .isEqualTo(AuroraDualKernelRuntime.PlannerStatus.SUCCEEDED);
        assertThat(client.latestPlannerSystemPrompt)
                .contains("没有默认数量", "让 1、2、3 条随真实语义自然变化", "不得随机抽数");

        AuroraDualKernelRuntime.Generation second = runtime.generate(7L, "DAILY_TALK",
                Map.of("sessionId", 91L, "userMessage", "但下午又困了",
                        "agentLoopPolicy", "按语境选择数量"),
                client, StructuredAiResults.AuroraResult::new);
        assertThat(second.guidanceSource()).isEqualTo("real");
        assertThat(client.latestSpeakerRequestJson).contains("下一轮少分析，顺着具体感受接话");

        plannerPool.shutdownNow();
    }

    @Test
    void exorcistContinuationKeepsPreviousTurnGuidance() throws Exception {
        RuntimeFixture fixture = fixture();
        try {
            completeFirstTurn(fixture, "我刚读完《驱魔人》，梅林神父的选择让我很震动");

            AuroraDualKernelRuntime.Generation next = fixture.runtime.generate(7L, "DAILY_TALK",
                    Map.of("sessionId", 91L, "userMessage", "然后呢？",
                            "agentLoopPolicy", "按语境选择数量"),
                    fixture.client, StructuredAiResults.AuroraResult::new);

            assertThat(next.guidanceSource()).isEqualTo("real");
            assertThat(fixture.client.latestSpeakerRequestJson).contains("\"dialogueGuidance\"");
        } finally {
            fixture.pool.shutdownNow();
        }
    }

    @Test
    void explicitSwitchFromExorcistToSpiritedAwayRejectsStaleGuidance() throws Exception {
        RuntimeFixture fixture = fixture();
        try {
            completeFirstTurn(fixture, "我刚读完《驱魔人》，梅林神父的选择让我很震动");

            AuroraDualKernelRuntime.Generation next = fixture.runtime.generate(7L, "DAILY_TALK",
                    Map.of("sessionId", 91L, "userMessage", "换个话题，我们聊《千与千寻》吧",
                            "agentLoopPolicy", "按语境选择数量"),
                    fixture.client, StructuredAiResults.AuroraResult::new);

            assertThat(next.guidanceSource()).isEqualTo("topic-shift");
            assertThat(fixture.client.latestSpeakerRequestJson)
                    .doesNotContain("\"dialogueGuidance\"")
                    .contains("\"guidanceUnavailable\":\"topic-shift\"");
        } finally {
            fixture.pool.shutdownNow();
        }
    }

    @Test
    void namedTopicChangeRejectsStaleGuidanceEvenWithoutSwitchPhrase() throws Exception {
        RuntimeFixture fixture = fixture();
        try {
            completeFirstTurn(fixture, "我在读《驱魔人》，很在意梅林神父");

            AuroraDualKernelRuntime.Generation next = fixture.runtime.generate(7L, "DAILY_TALK",
                    Map.of("sessionId", 91L, "userMessage", "《千与千寻》的无脸男很有意思",
                            "agentLoopPolicy", "按语境选择数量"),
                    fixture.client, StructuredAiResults.AuroraResult::new);

            assertThat(next.guidanceSource()).isEqualTo("topic-shift");
            assertThat(fixture.client.latestSpeakerRequestJson).doesNotContain("\"dialogueGuidance\"");
        } finally {
            fixture.pool.shutdownNow();
        }
    }

    @Test
    void connectiveCannotOverrideNamedTopicMismatch() throws Exception {
        RuntimeFixture fixture = fixture();
        try {
            completeFirstTurn(fixture, "我在读《驱魔人》，很在意梅林神父");

            AuroraDualKernelRuntime.Generation next = fixture.runtime.generate(7L, "DAILY_TALK",
                    Map.of("sessionId", 91L, "userMessage", "但是《千与千寻》也很有意思",
                            "agentLoopPolicy", "按语境选择数量"),
                    fixture.client, StructuredAiResults.AuroraResult::new);

            assertThat(next.guidanceSource()).isEqualTo("topic-shift");
            assertThat(fixture.client.latestSpeakerRequestJson).doesNotContain("\"dialogueGuidance\"");
        } finally {
            fixture.pool.shutdownNow();
        }
    }

    private static RuntimeFixture fixture() {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        BlockingPlannerClient client = new BlockingPlannerClient();
        StructuredAiService structured = new StructuredAiService(client, ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(structured);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        runtime.setPlannerExecutor(pool);
        return new RuntimeFixture(runtime, client, pool);
    }

    private static void completeFirstTurn(RuntimeFixture fixture, String message) throws Exception {
        AuroraDualKernelRuntime.Generation first = fixture.runtime.generate(7L, "DAILY_TALK",
                Map.of("sessionId", 91L, "userMessage", message,
                        "agentLoopPolicy", "按语境选择数量"),
                fixture.client, StructuredAiResults.AuroraResult::new);
        assertThat(fixture.client.plannerStarted.await(1, TimeUnit.SECONDS)).isTrue();
        fixture.client.releasePlanner.countDown();
        assertThat(first.backgroundPlannerEvidence().get(2, TimeUnit.SECONDS).status())
                .isEqualTo(AuroraDualKernelRuntime.PlannerStatus.SUCCEEDED);
    }

    private record RuntimeFixture(AuroraDualKernelRuntime runtime,
                                  BlockingPlannerClient client,
                                  ExecutorService pool) {}

    private static final class BlockingPlannerClient implements LlmClient {
        private final CountDownLatch plannerStarted = new CountDownLatch(1);
        private final CountDownLatch releasePlanner = new CountDownLatch(1);
        private volatile String latestSpeakerRequestJson = "";
        private volatile String latestSpeakerSystemPrompt = "";
        private volatile String latestPlannerSystemPrompt = "";

        @Override
        public String chat(LlmRequest request) {
            if (request.moduleName.startsWith("AURORA_SPEAKER")) {
                latestSpeakerRequestJson = request.requestJson;
                latestSpeakerSystemPrompt = request.systemPrompt;
                return """
                    {"segments":["午饭这次选对了。"],"speakCount":1,
                     "continueReason":"自然接话","detectedTheme":"午饭","nextQuestion":"",
                     "smallStep":"","featureSuggestion":"","featureTarget":"",
                     "memoryReferenced":false,"referencedMemoryIds":[],"riskFlags":[]}
                    """;
            }
            if (request.moduleName.startsWith("AURORA_PLAN")) {
                latestPlannerSystemPrompt = request.systemPrompt;
                plannerStarted.countDown();
                try {
                    releasePlanner.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                return """
                    {"userIntent":"延续日常交流","emotionalNeed":"自然回应",
                     "relationshipMove":"下一轮少分析，顺着具体感受接话",
                     "responseConstraints":["不把日常小事上升为心理分析"],
                     "bubblePurposes":["直接接话"],"relevantMemoryIds":[],"uncertainty":"",
                     "needsCritic":false,"innerVoiceWorthy":false,"innerVoiceSeed":""}
                    """;
            }
            return """
                {"pass":true,"issues":[]}
                """;
        }

        @Override
        public SseEmitter streamChat(LlmRequest request) {
            return new SseEmitter();
        }
    }
}
