package com.innercosmos.ai.runtime;

import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.config.LlmConfig;
import com.innercosmos.service.ABTestService;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuroraDualKernelRuntimeTest {
    @Test
    void separatesPlanningSpeakingAndBoundedRepair() {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        RecordingClient client = new RecordingClient();
        StructuredAiService structured = new StructuredAiService(client, ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(structured);

        var generation = runtime.generate(7L, "DAILY_TALK",
            Map.of("userMessage", "先别分析，我只想被接住", "interruptionContext", "cancel old plan"),
            client, StructuredAiResults.AuroraResult::new);

        assertThat(client.modules).containsExactly(
            "AURORA_PLAN_DAILY_TALK", "AURORA_SPEAKER_DAILY_TALK", "AURORA_CRITIC_DAILY_TALK");
        assertThat(generation.runtime()).isEqualTo("dual-kernel.v1");
        assertThat(generation.repaired()).isTrue();
        assertThat(generation.result().segments).containsExactly("好，我先停在这里接住你，不往下分析。");
    }

    @Test
    void criticFailureRepairsObservableViolationWithSafeFallback() {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        CriticUnavailableClient client = new CriticUnavailableClient();
        StructuredAiService structured = new StructuredAiService(client, ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(structured);
        var safe = new StructuredAiResults.AuroraResult();
        safe.segments = List.of("我不确定那段记忆，所以只回应你现在明确说出的需要。");

        var generation = runtime.generate(7L, "DAILY_TALK", Map.of("userMessage", "只说现在"),
            client, () -> safe);

        assertThat(generation.repaired()).isTrue();
        assertThat(generation.criticIssues()).contains("unauthorized_memory_expansion");
        assertThat(generation.result().segments).isEqualTo(safe.segments);
    }

    private static final class RecordingClient implements LlmClient {
        private final List<String> modules = new ArrayList<>();

        @Override
        public String chat(LlmRequest request) {
            modules.add(request.moduleName);
            if (request.moduleName.startsWith("AURORA_PLAN")) return """
                {"userIntent":"停止分析","emotionalNeed":"先被接住","relationshipMove":"接受打断",
                 "responseConstraints":["不分析"],"bubblePurposes":["接住"],"relevantMemoryIds":[],
                 "uncertainty":"低","needsCritic":true}
                """;
            if (request.moduleName.startsWith("AURORA_SPEAKER")) return """
                {"segments":["我记得你去年也这样。"],"speakCount":1,"continueReason":"accept",
                 "detectedTheme":"害怕","memoryReferenced":true,"referencedMemoryIds":[99],"riskFlags":[]}
                """;
            return """
                {"pass":false,"issues":["unauthorized_memory_expansion"],"repaired":{
                 "segments":["好，我先停在这里接住你，不往下分析。"],"speakCount":1,
                 "continueReason":"repair","detectedTheme":"害怕","memoryReferenced":false,
                 "referencedMemoryIds":[],"riskFlags":[]}}
                """;
        }

        @Override public SseEmitter streamChat(LlmRequest request) { return new SseEmitter(); }
    }

    private static final class CriticUnavailableClient implements LlmClient {
        @Override
        public String chat(LlmRequest request) {
            if (request.moduleName.startsWith("AURORA_PLAN")) return """
                {"userIntent":"回应现在","emotionalNeed":"被听见","relationshipMove":"保持当下",
                 "responseConstraints":[],"bubblePurposes":["回应"],"relevantMemoryIds":[],
                 "uncertainty":"低","needsCritic":false}
                """;
            if (request.moduleName.startsWith("AURORA_SPEAKER")) return """
                {"segments":["我记得你去年也这样。"],"speakCount":1,"continueReason":"reply",
                 "detectedTheme":"现在","memoryReferenced":true,"referencedMemoryIds":[99],"riskFlags":[]}
                """;
            return "";
        }

        @Override public SseEmitter streamChat(LlmRequest request) { return new SseEmitter(); }
    }
}
