package com.innercosmos.ai.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.config.LlmConfig;
import com.innercosmos.service.ABTestService;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuroraRuntimeQualityRegressionTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void styleHeuristicsAreObservableButDoNotReplaceValidSpeakerTextWithCannedCopy() throws Exception {
        for (String phrase : List.of(
                "这种慢下来很自然，也不代表你不在乎。",
                "我在这里看到的是，你已经把边界说得很清楚。",
                "你现在这个节奏挺理性的。")) {
            AuroraDualKernelRuntime.Generation generation = generate(List.of(phrase), 11L);
            assertThat(generation.result().segments).containsExactly(phrase);
            assertThat(generation.repaired()).isFalse();
            assertThat(generation.criticIssues()).isNotEmpty();
        }
    }

    @Test
    void shortDailyTurnKeepsNaturalThreeBubbleVariation() throws Exception {
        AuroraDualKernelRuntime.Generation generation = generate(
                List.of("这顿选对了。", "下午那阵困意来得挺突然。", "后来缓过来了吗？"), 12L);

        assertThat(generation.result().segments).containsExactly(
                "这顿选对了。", "下午那阵困意来得挺突然。", "后来缓过来了吗？");
        assertThat(generation.result().speakCount).isEqualTo(3);
    }

    @Test
    void invalidFourthBubbleIsCappedWithoutConcatenatingItIntoTheThird() throws Exception {
        AuroraDualKernelRuntime.Generation generation = generate(
                List.of("一", "二", "三", "不应拼进第三条"), 13L);

        assertThat(generation.result().segments).containsExactly("一", "二", "三");
        assertThat(generation.result().segments.get(2)).doesNotContain("不应拼进第三条");
    }

    private AuroraDualKernelRuntime.Generation generate(List<String> segments, long sessionId) throws Exception {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        ScriptedClient client = new ScriptedClient(segments);
        StructuredAiService structured = new StructuredAiService(client, ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(structured);
        ExecutorService plannerPool = Executors.newSingleThreadExecutor();
        runtime.setPlannerExecutor(plannerPool);
        try {
            return runtime.generate(7L, "DAILY_TALK",
                    Map.of("sessionId", sessionId, "userMessage", "午饭还不错",
                            "agentLoopPolicy", "按语境选择数量"),
                    client, StructuredAiResults.AuroraResult::new);
        } finally {
            plannerPool.shutdownNow();
        }
    }

    private static final class ScriptedClient implements LlmClient {
        private final String speakerJson;

        private ScriptedClient(List<String> segments) throws Exception {
            speakerJson = JSON.writeValueAsString(Map.ofEntries(
                    Map.entry("segments", segments),
                    Map.entry("speakCount", segments.size()),
                    Map.entry("continueReason", "自然接话"),
                    Map.entry("detectedTheme", "日常"),
                    Map.entry("nextQuestion", ""),
                    Map.entry("smallStep", ""),
                    Map.entry("featureSuggestion", ""),
                    Map.entry("featureTarget", ""),
                    Map.entry("memoryReferenced", false),
                    Map.entry("referencedMemoryIds", List.of()),
                    Map.entry("riskFlags", List.of())));
        }

        @Override
        public String chat(LlmRequest request) {
            if (request.moduleName.startsWith("AURORA_SPEAKER")) return speakerJson;
            if (request.moduleName.startsWith("AURORA_PLAN")) {
                return """
                    {"userIntent":"延续日常交流","emotionalNeed":"自然回应",
                     "relationshipMove":"顺着具体感受接话","responseConstraints":["不虚构"],
                     "bubblePurposes":["自然回应"],"relevantMemoryIds":[],"uncertainty":"",
                     "needsCritic":false,"innerVoiceWorthy":false,"innerVoiceSeed":""}
                    """;
            }
            return "{\"pass\":true,\"issues\":[]}";
        }

        @Override
        public SseEmitter streamChat(LlmRequest request) {
            return new SseEmitter();
        }
    }
}
