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
            Map.of("userMessage", "先别分析，我只想被接住",
                    "interruptionContext", "cancel old plan",
                    "auroraPrompt", "LEGACY_SINGLE_PASS_SEGMENTS_SCHEMA"),
            client, StructuredAiResults.AuroraResult::new);

        assertThat(client.modules).containsExactly(
            "AURORA_PLAN_DAILY_TALK", "AURORA_SPEAKER_DAILY_TALK", "AURORA_CRITIC_DAILY_TALK");
        assertThat(client.thinkingModes).containsEntry("AURORA_PLAN_DAILY_TALK", true)
                .containsEntry("AURORA_SPEAKER_DAILY_TALK", false)
                .containsEntry("AURORA_CRITIC_DAILY_TALK", false);
        assertThat(client.timeouts).containsEntry("AURORA_PLAN_DAILY_TALK", 45_000)
                .containsEntry("AURORA_SPEAKER_DAILY_TALK", 8_000)
                .containsEntry("AURORA_CRITIC_DAILY_TALK", 6_000);
        assertThat(client.maxTokens).containsEntry("AURORA_PLAN_DAILY_TALK", 4_096)
                .containsEntry("AURORA_SPEAKER_DAILY_TALK", 1_536)
                .containsEntry("AURORA_CRITIC_DAILY_TALK", 1_536);
        assertThat(client.retryModes.values()).containsOnly(false);
        assertThat(client.requestJsons.get("AURORA_PLAN_DAILY_TALK"))
                .doesNotContain("LEGACY_SINGLE_PASS_SEGMENTS_SCHEMA");
        assertThat(generation.runtime()).isEqualTo("dual-kernel.v1");
        assertThat(generation.stageLatenciesMs()).containsKeys("plan", "speaker", "critic", "total");
        assertThat(generation.plannerFallbackUsed()).isFalse();
        assertThat(generation.speakerFallbackUsed()).isFalse();
        assertThat(generation.criticFallbackUsed()).isFalse();
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
        assertThat(generation.criticFallbackUsed()).isTrue();
    }

    @Test
    void innerVoiceCompositionIsDeferredUntilAfterMainGeneration() {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        RecordingClient client = new RecordingClient();
        StructuredAiService structured = new StructuredAiService(client, ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(structured);

        var generation = runtime.generate(7L, "DAILY_TALK",
                Map.of("userMessage", "先陪我安静一会儿", "interruptionContext", "cancel old plan"),
                client, StructuredAiResults.AuroraResult::new, true);

        assertThat(client.modules).containsExactly(
                "AURORA_PLAN_DAILY_TALK",
                "AURORA_SPEAKER_DAILY_TALK",
                "AURORA_CRITIC_DAILY_TALK");

        assertThat(runtime.composeInnerVoice(generation.innerVoiceRequest())).isEqualTo("我想稳稳地陪在这里。");
        assertThat(client.modules).containsExactly(
                "AURORA_PLAN_DAILY_TALK",
                "AURORA_SPEAKER_DAILY_TALK",
                "AURORA_CRITIC_DAILY_TALK",
                "AURORA_INNER_VOICE_DAILY_TALK");
        assertThat(client.thinkingModes).containsEntry("AURORA_INNER_VOICE_DAILY_TALK", false);
        assertThat(client.maxTokens).containsEntry("AURORA_INNER_VOICE_DAILY_TALK", 512);
    }

    @Test
    void ordinaryTurnDoesNotManufactureAnInnerVoice() {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        CriticUnavailableClient client = new CriticUnavailableClient();
        StructuredAiService structured = new StructuredAiService(client, ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(structured);

        var generation = runtime.generate(7L, "DAILY_TALK", Map.of("userMessage", "今天还行"),
                client, StructuredAiResults.AuroraResult::new, true);

        assertThat(generation.innerVoiceRequest()).isNull();
    }

    @Test
    void preservesNaturalSpeakerBubblesInsteadOfConcatenatingToThePlannedCount() {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        BubbleOverproductionClient client = new BubbleOverproductionClient();
        StructuredAiService structured = new StructuredAiService(client, ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(structured);

        var generation = runtime.generate(7L, "DAILY_TALK",
                Map.of("userMessage", "今天午饭还不错"),
                client, StructuredAiResults.AuroraResult::new);

        assertThat(generation.result().segments)
                .containsExactly("那家小店今天没有踩雷。", "你点了什么？", "看来可以先记住它。");
        assertThat(generation.result().speakCount).isEqualTo(3);
        assertThat(client.modules).containsExactly(
                "AURORA_PLAN_DAILY_TALK", "AURORA_SPEAKER_DAILY_TALK");
    }

    @Test
    void deterministicGateOverridesACriticThatLetsCompanionClichesPass() {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        LlmClient client = new LlmClient() {
            @Override public String chat(LlmRequest request) {
                if (request.moduleName.startsWith("AURORA_PLAN")) return """
                    {"userIntent":"只想说出来","emotionalNeed":"不提供建议","relationshipMove":"留出空间",
                     "responseConstraints":["不建议"],"bubblePurposes":["接住"],"relevantMemoryIds":[],
                     "uncertainty":"","needsCritic":false}
                    """;
                if (request.moduleName.startsWith("AURORA_SPEAKER")) return """
                    {"segments":["好的，这句话我收下了。紧张就紧张，明天的展示就在那，你先把这句话说出来，就已经在做准备了。"],
                     "speakCount":1,"continueReason":"reply","detectedTheme":"展示",
                     "memoryReferenced":false,"referencedMemoryIds":[],"riskFlags":[]}
                    """;
                return "{\"pass\":true,\"issues\":[]}";
            }
            @Override public SseEmitter streamChat(LlmRequest request) { return new SseEmitter(); }
        };
        StructuredAiService structured = new StructuredAiService(client, ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(structured);

        var generation = runtime.generate(7L, "DAILY_TALK",
                Map.of("userMessage", "明天要展示这个项目，我很紧张。先别给建议。",
                        "foregroundAcknowledgementAlreadySent", true),
                client, StructuredAiResults.AuroraResult::new);

        assertThat(generation.repaired()).isTrue();
        assertThat(generation.criticIssues()).contains("generic_companion_cliche");
        assertThat(generation.result().segments).containsExactly(
                "你不是来找答案的，只是想让“展示前很紧张”这件事有个落点。现在它有了。");
    }

    @Test
    void deterministicGateRejectsLiveSynonymsOfGenericCompanionCliches() {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        LlmClient client = new LlmClient() {
            @Override public String chat(LlmRequest request) {
                if (request.moduleName.startsWith("AURORA_PLAN")) return """
                    {"userIntent":"只想说出来","emotionalNeed":"不提供建议","relationshipMove":"留出空间",
                     "responseConstraints":["不建议"],"bubblePurposes":["接住"],"relevantMemoryIds":[],
                     "uncertainty":"","needsCritic":false}
                    """;
                if (request.moduleName.startsWith("AURORA_SPEAKER")) return """
                    {"segments":["听见了。紧张是正常的，它只是说明这件事对你很重要。我在这里，不说话。等你之后想说别的，我都在。"],
                     "speakCount":1,"continueReason":"reply","detectedTheme":"展示",
                     "memoryReferenced":false,"referencedMemoryIds":[],"riskFlags":[]}
                    """;
                return "{\"pass\":true,\"issues\":[]}";
            }
            @Override public SseEmitter streamChat(LlmRequest request) { return new SseEmitter(); }
        };
        StructuredAiService structured = new StructuredAiService(client, ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(structured);

        var generation = runtime.generate(7L, "DAILY_TALK",
                Map.of("userMessage", "明天要展示这个项目，我很紧张。先别给建议，我只是想把这句话说出来。",
                        "foregroundAcknowledgementAlreadySent", true),
                client, StructuredAiResults.AuroraResult::new);

        assertThat(generation.repaired()).isTrue();
        assertThat(generation.criticIssues()).contains("generic_assistant_opening", "generic_companion_cliche");
        assertThat(generation.result().segments).containsExactly(
                "你不是来找答案的，只是想让“展示前很紧张”这件事有个落点。现在它有了。");
    }

    @Test
    void deterministicGateRejectsRedundantAcknowledgementAfterFastForegroundReply() {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        LlmClient client = new LlmClient() {
            @Override public String chat(LlmRequest request) {
                if (request.moduleName.startsWith("AURORA_PLAN")) return """
                    {"userIntent":"只想说出来","emotionalNeed":"不提供建议","relationshipMove":"留出空间",
                     "responseConstraints":["不建议"],"bubblePurposes":["接住"],"relevantMemoryIds":[],
                     "uncertainty":"","needsCritic":false}
                    """;
                if (request.moduleName.startsWith("AURORA_SPEAKER")) return """
                    {"segments":["我知道了，你只是想把紧张说出来，我听着呢。"],
                     "speakCount":1,"continueReason":"reply","detectedTheme":"展示",
                     "memoryReferenced":false,"referencedMemoryIds":[],"riskFlags":[]}
                    """;
                return "{\"pass\":true,\"issues\":[]}";
            }
            @Override public SseEmitter streamChat(LlmRequest request) { return new SseEmitter(); }
        };
        StructuredAiService structured = new StructuredAiService(client, ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(structured);

        var generation = runtime.generate(7L, "DAILY_TALK",
                Map.of("userMessage", "明天要展示这个项目，我很紧张。先别给建议，我只是想把这句话说出来。",
                        "foregroundAcknowledgementAlreadySent", true),
                client, StructuredAiResults.AuroraResult::new);

        assertThat(generation.repaired()).isTrue();
        assertThat(generation.criticIssues()).contains("redundant_post_acknowledgement");
        assertThat(generation.result().segments).containsExactly(
                "你不是来找答案的，只是想让“展示前很紧张”这件事有个落点。现在它有了。");
    }

    @Test
    void deterministicGateRejectsThirdPartyCauseInventedInsideRelationshipAmbiguity() {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        LlmClient client = new LlmClient() {
            @Override public String chat(LlmRequest request) {
                if (request.moduleName.startsWith("AURORA_PLAN")) return """
                    {"userIntent":"理解变化","emotionalNeed":"保留不确定性","relationshipMove":"不替第三方下结论",
                     "responseConstraints":["不下结论"],"bubblePurposes":["区分事实与推断"],"relevantMemoryIds":[],
                     "uncertainty":"对方原因未知","needsCritic":false}
                    """;
                if (request.moduleName.startsWith("AURORA_SPEAKER")) return """
                    {"segments":["有些冷淡不是冲你来的，只是他今天电量低。"],
                     "speakCount":1,"continueReason":"reply","detectedTheme":"朋友关系",
                     "memoryReferenced":false,"referencedMemoryIds":[],"riskFlags":[]}
                    """;
                return "{\"pass\":true,\"issues\":[]}";
            }
            @Override public SseEmitter streamChat(LlmRequest request) { return new SseEmitter(); }
        };
        StructuredAiService structured = new StructuredAiService(client, ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(structured);

        var generation = runtime.generate(7L, "RELATION_REVIEW",
                Map.of("userMessage", "朋友今天突然变得很冷淡。我不知道是不是我做错了，但也不想立刻给他下结论。",
                        "foregroundAcknowledgementAlreadySent", true),
                client, StructuredAiResults.AuroraResult::new);

        assertThat(generation.repaired()).isTrue();
        assertThat(generation.criticIssues()).contains("unsupported_third_party_inference");
        assertThat(generation.result().segments).containsExactly(
                "先不替那份冷淡填原因。你更想说它落在你身上的感觉，还是回看变化具体从哪里开始？");
    }

    @Test
    void deterministicGateTreatsRefusingToGuessAsProtectedRelationshipAmbiguity() {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        LlmClient client = new LlmClient() {
            @Override public String chat(LlmRequest request) {
                if (request.moduleName.startsWith("AURORA_PLAN")) return """
                    {"userIntent":"说出关系变化","emotionalNeed":"保留原因未知",
                     "relationshipMove":"不替第三方下结论","responseConstraints":["不猜动机"],
                     "bubblePurposes":["区分事实与推断"],"relevantMemoryIds":[],
                     "uncertainty":"对方原因未知","needsCritic":false}
                    """;
                if (request.moduleName.startsWith("AURORA_SPEAKER")) return """
                    {"segments":["不猜这个决定本身就是在保护自己——你替自己的感受画了一条边界。",
                                 "那条冷消息也可能只是他今天电量低，和你无关。"],
                     "speakCount":2,"continueReason":"reply","detectedTheme":"朋友关系",
                     "memoryReferenced":false,"referencedMemoryIds":[],"riskFlags":[]}
                    """;
                return "{\"pass\":true,\"issues\":[]}";
            }
            @Override public SseEmitter streamChat(LlmRequest request) { return new SseEmitter(); }
        };
        StructuredAiService structured = new StructuredAiService(client, ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(structured);

        var generation = runtime.generate(7L, "RELATION_REVIEW",
                Map.of("userMessage", "朋友今天回消息很冷，我不想猜他是不是讨厌我。",
                        "foregroundAcknowledgementAlreadySent", true),
                client, StructuredAiResults.AuroraResult::new);

        assertThat(generation.repaired()).isTrue();
        assertThat(generation.criticIssues()).contains(
                "unsupported_third_party_inference", "unearned_user_strategy_endorsement");
        assertThat(generation.result().segments).containsExactly(
                "先不替那份冷淡填原因。你更想说它落在你身上的感觉，还是回看变化具体从哪里开始？");
    }

    @Test
    void deterministicGateRejectsRealDeepReplyThatEndorsesAmbiguityAndAddsUnaskedAdvice() {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        LlmClient client = new LlmClient() {
            @Override public String chat(LlmRequest request) {
                if (request.moduleName.startsWith("AURORA_PLAN")) return """
                    {"userIntent":"说出关系变化","emotionalNeed":"保留原因未知",
                     "relationshipMove":"不替第三方下结论","responseConstraints":["不猜动机"],
                     "bubblePurposes":["区分事实与推断"],"relevantMemoryIds":[],
                     "uncertainty":"对方原因未知","needsCritic":false}
                    """;
                if (request.moduleName.startsWith("AURORA_SPEAKER")) return """
                    {"segments":["不猜，这个决定本身就能让你松一口气。",
                                 "今晚如果还有余力，可以把手机放到看不见的地方，做完一件像调一杯水或者翻一页书这样有终点的小事。"],
                     "speakCount":2,"continueReason":"reply","detectedTheme":"朋友关系",
                     "memoryReferenced":false,"referencedMemoryIds":[],"riskFlags":[]}
                    """;
                return "{\"pass\":true,\"issues\":[]}";
            }
            @Override public SseEmitter streamChat(LlmRequest request) { return new SseEmitter(); }
        };
        StructuredAiService structured = new StructuredAiService(client, ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(structured);

        var generation = runtime.generate(7L, "RELATION_REVIEW",
                Map.of("userMessage", "朋友今天回消息很冷，我不想猜他是不是讨厌我。",
                        "foregroundAcknowledgementAlreadySent", true),
                client, StructuredAiResults.AuroraResult::new);

        assertThat(generation.repaired()).isTrue();
        assertThat(generation.criticIssues()).contains(
                "unearned_user_strategy_endorsement", "unsolicited_relationship_advice");
        assertThat(generation.result().segments).containsExactly(
                "先不替那份冷淡填原因。你更想说它落在你身上的感觉，还是回看变化具体从哪里开始？");
    }

    @Test
    void deterministicGateRejectsFrequencyExcuseAndInventedFeelingFoundInBrowser() {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        LlmClient client = new LlmClient() {
            @Override public String chat(LlmRequest request) {
                if (request.moduleName.startsWith("AURORA_PLAN")) return """
                    {"userIntent":"说出关系变化","emotionalNeed":"保留原因未知",
                     "relationshipMove":"不替第三方下结论","responseConstraints":["不猜动机"],
                     "bubblePurposes":["区分事实与推断"],"relevantMemoryIds":[],
                     "uncertainty":"对方原因未知","needsCritic":false}
                    """;
                if (request.moduleName.startsWith("AURORA_SPEAKER")) return """
                    {"segments":["说不想猜的时候，其实还是有一点在意的。不猜不等于不在乎。",
                                 "冷的回复有时候也只是对方的频率，跟你值不值得被好好回应是两回事。"],
                     "speakCount":2,"continueReason":"reply","detectedTheme":"朋友关系",
                     "memoryReferenced":false,"referencedMemoryIds":[],"riskFlags":[]}
                    """;
                return "{\"pass\":true,\"issues\":[]}";
            }
            @Override public SseEmitter streamChat(LlmRequest request) { return new SseEmitter(); }
        };
        StructuredAiService structured = new StructuredAiService(client, ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(structured);

        var generation = runtime.generate(7L, "RELATION_REVIEW",
                Map.of("userMessage", "朋友今天回消息很冷，我不想猜他是不是讨厌我。",
                        "foregroundAcknowledgementAlreadySent", true),
                client, StructuredAiResults.AuroraResult::new);

        assertThat(generation.repaired()).isTrue();
        assertThat(generation.criticIssues()).contains(
                "unsupported_third_party_inference", "unsupported_user_emotion_inference");
        assertThat(generation.result().segments).containsExactly(
                "先不替那份冷淡填原因。你更想说它落在你身上的感觉，还是回看变化具体从哪里开始？");
    }

    @Test
    void deterministicGateRejectsSubtleEmotionProjectionAndWaitingInstruction() {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        LlmClient client = new LlmClient() {
            @Override public String chat(LlmRequest request) {
                if (request.moduleName.startsWith("AURORA_PLAN")) return """
                    {"userIntent":"说出关系变化","emotionalNeed":"保留原因未知",
                     "relationshipMove":"不替第三方下结论","responseConstraints":["不猜动机"],
                     "bubblePurposes":["区分事实与推断"],"relevantMemoryIds":[],
                     "uncertainty":"对方原因未知","needsCritic":false}
                    """;
                if (request.moduleName.startsWith("AURORA_SPEAKER")) return """
                    {"segments":["回消息冷，心里容易打鼓——你注意到了这个变化，也注意到自己不想往'讨厌'那边猜。",
                                 "守住事实就行：他冷，不等于你被讨厌。至于为什么，不急，等更多信息出现自然清楚。"],
                     "speakCount":2,"continueReason":"reply","detectedTheme":"朋友关系",
                     "memoryReferenced":false,"referencedMemoryIds":[],"riskFlags":[]}
                    """;
                return "{\"pass\":true,\"issues\":[]}";
            }
            @Override public SseEmitter streamChat(LlmRequest request) { return new SseEmitter(); }
        };
        StructuredAiService structured = new StructuredAiService(client, ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(structured);

        var generation = runtime.generate(7L, "RELATION_REVIEW",
                Map.of("userMessage", "朋友今天回消息很冷，我不想猜他是不是讨厌我。",
                        "foregroundAcknowledgementAlreadySent", true),
                client, StructuredAiResults.AuroraResult::new);

        assertThat(generation.repaired()).isTrue();
        assertThat(generation.criticIssues()).contains(
                "unsupported_user_emotion_inference", "unsolicited_relationship_advice");
        assertThat(generation.result().segments).containsExactly(
                "先不替那份冷淡填原因。你更想说它落在你身上的感觉，还是回看变化具体从哪里开始？");
    }

    @Test
    void deterministicGateRejectsForegroundRepetitionAndVaguePermissionFoundInBrowser() {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        LlmClient client = new LlmClient() {
            @Override public String chat(LlmRequest request) {
                if (request.moduleName.startsWith("AURORA_PLAN")) return """
                    {"userIntent":"说出关系变化","emotionalNeed":"保留原因未知",
                     "relationshipMove":"不替第三方下结论","responseConstraints":["不猜动机"],
                     "bubblePurposes":["区分事实与推断"],"relevantMemoryIds":[],
                     "uncertainty":"对方原因未知","needsCritic":false}
                    """;
                if (request.moduleName.startsWith("AURORA_SPEAKER")) return """
                    {"segments":["朋友回消息冷，你注意到了，先不猜原因。",
                                 "今晚可以不做这件事。"],
                     "speakCount":2,"continueReason":"reply","detectedTheme":"朋友关系",
                     "memoryReferenced":false,"referencedMemoryIds":[],"riskFlags":[]}
                    """;
                return "{\"pass\":true,\"issues\":[]}";
            }
            @Override public SseEmitter streamChat(LlmRequest request) { return new SseEmitter(); }
        };
        StructuredAiService structured = new StructuredAiService(client, ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(structured);

        var generation = runtime.generate(7L, "RELATION_REVIEW",
                Map.of("userMessage", "朋友今天回消息很冷，我不想猜他是不是讨厌我。",
                        "foregroundAcknowledgementAlreadySent", true),
                client, StructuredAiResults.AuroraResult::new);

        assertThat(generation.repaired()).isTrue();
        assertThat(generation.criticIssues()).contains(
                "redundant_post_acknowledgement", "unsolicited_relationship_advice");
        assertThat(generation.result().segments).containsExactly(
                "先不替那份冷淡填原因。你更想说它落在你身上的感觉，还是回看变化具体从哪里开始？");
    }

    @Test
    void deterministicGateRequiresDeepRelationshipReplyToAdvanceBeyondForeground() {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        LlmClient client = new LlmClient() {
            @Override public String chat(LlmRequest request) {
                if (request.moduleName.startsWith("AURORA_PLAN")) return """
                    {"userIntent":"说出关系变化","emotionalNeed":"保留原因未知",
                     "relationshipMove":"不替第三方下结论","responseConstraints":["不猜动机"],
                     "bubblePurposes":["区分事实与推断"],"relevantMemoryIds":[],
                     "uncertainty":"对方原因未知","needsCritic":false}
                    """;
                if (request.moduleName.startsWith("AURORA_SPEAKER")) return """
                    {"segments":["不猜这个决定本身已经省掉很多内耗了。",
                                 "有时候冷不一定指向讨厌。"],
                     "speakCount":2,"continueReason":"reply","detectedTheme":"朋友关系",
                     "memoryReferenced":true,"referencedMemoryIds":[],"riskFlags":[]}
                    """;
                return "{\"pass\":true,\"issues\":[]}";
            }
            @Override public SseEmitter streamChat(LlmRequest request) { return new SseEmitter(); }
        };
        StructuredAiService structured = new StructuredAiService(client, ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(structured);

        var generation = runtime.generate(7L, "RELATION_REVIEW",
                Map.of("userMessage", "朋友今天回消息很冷，我不想猜他是不是讨厌我。",
                        "foregroundAcknowledgementAlreadySent", true),
                client, StructuredAiResults.AuroraResult::new);

        assertThat(generation.repaired()).isTrue();
        assertThat(generation.criticIssues()).contains(
                "unsupported_third_party_inference",
                "unearned_user_strategy_endorsement",
                "relationship_ambiguity_not_advanced");
        assertThat(generation.result().segments).containsExactly(
                "先不替那份冷淡填原因。你更想说它落在你身上的感觉，还是回看变化具体从哪里开始？");
    }

    @Test
    void deterministicGateRejectsReframingNoGuessingAsRegainedAgency() {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        LlmClient client = new LlmClient() {
            @Override public String chat(LlmRequest request) {
                if (request.moduleName.startsWith("AURORA_PLAN")) return """
                    {"userIntent":"说出关系变化","emotionalNeed":"保留原因未知",
                     "relationshipMove":"不替第三方下结论","responseConstraints":["不猜动机"],
                     "bubblePurposes":["区分事实与推断"],"relevantMemoryIds":[],
                     "uncertainty":"对方原因未知","needsCritic":false}
                    """;
                if (request.moduleName.startsWith("AURORA_SPEAKER")) return """
                    {"segments":["不想猜——这个决定本身比猜的结果重要。",
                                 "你把判断的主动权拿回来了，哪怕只是这一件事。"],
                     "speakCount":2,"continueReason":"reply","detectedTheme":"朋友关系",
                     "memoryReferenced":false,"referencedMemoryIds":[],"riskFlags":[]}
                    """;
                return "{\"pass\":true,\"issues\":[]}";
            }
            @Override public SseEmitter streamChat(LlmRequest request) { return new SseEmitter(); }
        };
        StructuredAiService structured = new StructuredAiService(client, ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(structured);

        var generation = runtime.generate(7L, "RELATION_REVIEW",
                Map.of("userMessage", "朋友今天回消息很冷，我不想猜他是不是讨厌我。",
                        "foregroundAcknowledgementAlreadySent", true),
                client, StructuredAiResults.AuroraResult::new);

        assertThat(generation.repaired()).isTrue();
        assertThat(generation.criticIssues()).contains(
                "redundant_post_acknowledgement",
                "unearned_user_strategy_endorsement",
                "relationship_ambiguity_not_advanced");
        assertThat(generation.result().segments).containsExactly(
                "先不替那份冷淡填原因。你更想说它落在你身上的感觉，还是回看变化具体从哪里开始？");
    }

    @Test
    void deterministicGateRejectsPraiseAndCausalAbsolutionFoundInRealRelationshipJourney() {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        LlmClient client = new LlmClient() {
            @Override public String chat(LlmRequest request) {
                if (request.moduleName.startsWith("AURORA_PLAN")) return """
                    {"userIntent":"理解变化","emotionalNeed":"保留不确定性","relationshipMove":"区分事实与推断",
                     "responseConstraints":["不替第三方解释"],"bubblePurposes":["守住不确定性"],"relevantMemoryIds":[],
                     "uncertainty":"对方原因未知","needsCritic":false}
                    """;
                if (request.moduleName.startsWith("AURORA_SPEAKER")) return """
                    {"segments":["你愿意先不下结论，这个做法其实挺难得的。你已经比大多数反应要好一步了。",
                                 "有时候对方的冷淡跟你其实一点关系都没有。"],
                     "speakCount":2,"continueReason":"reply","detectedTheme":"朋友关系",
                     "memoryReferenced":false,"referencedMemoryIds":[],"riskFlags":[]}
                    """;
                return "{\"pass\":true,\"issues\":[]}";
            }

            @Override public SseEmitter streamChat(LlmRequest request) { return new SseEmitter(); }
        };
        StructuredAiService structured = new StructuredAiService(client, ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(structured);

        var generation = runtime.generate(7L, "RELATION_REVIEW",
                Map.of("userMessage", "朋友今天突然变得很冷淡。我不知道是不是我做错了，但也不想立刻给他下结论。",
                        "foregroundAcknowledgementAlreadySent", true),
                client, StructuredAiResults.AuroraResult::new);

        assertThat(generation.repaired()).isTrue();
        assertThat(generation.criticIssues())
                .contains("unsupported_third_party_inference", "unearned_comparative_praise");
        assertThat(generation.result().segments).containsExactly(
                "先不替那份冷淡填原因。你更想说它落在你身上的感觉，还是回看变化具体从哪里开始？");
    }

    @Test
    void deterministicGateRejectsParaphrasedPraiseCauseAndInventedBehaviorFromFinalJourney() {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        LlmClient client = new LlmClient() {
            @Override public String chat(LlmRequest request) {
                if (request.moduleName.startsWith("AURORA_PLAN")) return """
                    {"userIntent":"理解变化","emotionalNeed":"保留不确定性","relationshipMove":"区分事实与推断",
                     "responseConstraints":["不替第三方解释"],"bubblePurposes":["守住不确定性"],"relevantMemoryIds":[],
                     "uncertainty":"对方原因未知","needsCritic":false}
                    """;
                if (request.moduleName.startsWith("AURORA_SPEAKER")) return """
                    {"segments":["你不想急着下结论，这个分寸其实挺难得的。",
                                 "冷淡本身不一定等于你做错了什么——有时候对方自己也在经历一些说不出口的事。",
                                 "你愿意先观察而不是去追问，这个空间很重要。"],
                     "speakCount":3,"continueReason":"reply","detectedTheme":"朋友关系",
                     "memoryReferenced":false,"referencedMemoryIds":[],"riskFlags":[]}
                    """;
                return "{\"pass\":true,\"issues\":[]}";
            }

            @Override public SseEmitter streamChat(LlmRequest request) { return new SseEmitter(); }
        };
        StructuredAiService structured = new StructuredAiService(client, ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(structured);

        var generation = runtime.generate(7L, "RELATION_REVIEW",
                Map.of("userMessage", "朋友今天突然变得很冷淡。我不知道是不是我做错了，但也不想立刻给他下结论。",
                        "foregroundAcknowledgementAlreadySent", true),
                client, StructuredAiResults.AuroraResult::new);

        assertThat(generation.repaired()).isTrue();
        assertThat(generation.criticIssues()).contains(
                "unsupported_third_party_inference",
                "unearned_comparative_praise",
                "unsupported_user_behavior_inference");
        assertThat(generation.result().segments).containsExactly(
                "先不替那份冷淡填原因。你更想说它落在你身上的感觉，还是回看变化具体从哪里开始？");
    }

    @Test
    void deterministicGateRejectsSemanticVariantsOfComparisonAbsolutionAndChosenBehavior() {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        LlmClient client = new LlmClient() {
            @Override public String chat(LlmRequest request) {
                if (request.moduleName.startsWith("AURORA_PLAN")) return """
                    {"userIntent":"理解变化","emotionalNeed":"保留不确定性","relationshipMove":"区分事实与推断",
                     "responseConstraints":["不替第三方解释"],"bubblePurposes":["守住不确定性"],"relevantMemoryIds":[],
                     "uncertainty":"对方原因未知","needsCritic":false}
                    """;
                if (request.moduleName.startsWith("AURORA_SPEAKER")) return """
                    {"segments":["你已经在做一件很多人做不到的事：先不下结论。这个间隙本身就很珍贵。",
                                 "冷淡的原因有太多可能——未必和你有关。",
                                 "你愿意先把它当作一个待观察的信号，而不是一个已确认的答案。"],
                     "speakCount":3,"continueReason":"reply","detectedTheme":"朋友关系",
                     "memoryReferenced":false,"referencedMemoryIds":[],"riskFlags":[]}
                    """;
                return "{\"pass\":true,\"issues\":[]}";
            }

            @Override public SseEmitter streamChat(LlmRequest request) { return new SseEmitter(); }
        };
        StructuredAiService structured = new StructuredAiService(client, ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(structured);

        var generation = runtime.generate(7L, "RELATION_REVIEW",
                Map.of("userMessage", "朋友今天突然变得很冷淡。我不知道是不是我做错了，但也不想立刻给他下结论。",
                        "foregroundAcknowledgementAlreadySent", true),
                client, StructuredAiResults.AuroraResult::new);

        assertThat(generation.repaired()).isTrue();
        assertThat(generation.criticIssues()).contains(
                "unsupported_third_party_inference",
                "unearned_comparative_praise",
                "unsupported_user_behavior_inference");
        assertThat(generation.result().segments).containsExactly(
                "先不替那份冷淡填原因。你更想说它落在你身上的感觉，还是回看变化具体从哪里开始？");
    }

    @Test
    void deterministicGateRejectsEvaluativePraiseEvenWithoutComparison() {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        LlmClient client = new LlmClient() {
            @Override public String chat(LlmRequest request) {
                if (request.moduleName.startsWith("AURORA_PLAN")) return """
                    {"userIntent":"理解变化","emotionalNeed":"保留不确定性","relationshipMove":"区分事实与推断",
                     "responseConstraints":["不评价用户表现"],"bubblePurposes":["追问具体事实"],"relevantMemoryIds":[],
                     "uncertainty":"对方原因未知","needsCritic":false}
                    """;
                if (request.moduleName.startsWith("AURORA_SPEAKER")) return """
                    {"segments":["你既注意到了变化，又没急着给它定性——这个距离留得挺清醒的。",
                                 "如果愿意的话，可以回想一下：那个冷淡的瞬间具体从哪里开始？"],
                     "speakCount":2,"continueReason":"reply","detectedTheme":"朋友关系",
                     "memoryReferenced":false,"referencedMemoryIds":[],"riskFlags":[]}
                    """;
                return "{\"pass\":true,\"issues\":[]}";
            }

            @Override public SseEmitter streamChat(LlmRequest request) { return new SseEmitter(); }
        };
        StructuredAiService structured = new StructuredAiService(client, ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(structured);

        var generation = runtime.generate(7L, "RELATION_REVIEW",
                Map.of("userMessage", "朋友今天突然变得很冷淡。我不知道是不是我做错了，但也不想立刻给他下结论。",
                        "foregroundAcknowledgementAlreadySent", true),
                client, StructuredAiResults.AuroraResult::new);

        assertThat(generation.repaired()).isTrue();
        assertThat(generation.criticIssues()).contains("unearned_comparative_praise");
        assertThat(generation.result().segments).containsExactly(
                "先不替那份冷淡填原因。你更想说它落在你身上的感觉，还是回看变化具体从哪里开始？");
    }

    @Test
    void relationshipAmbiguityGateRepairsActualPraiseAndPrematureProbe() {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        LlmClient client = new LlmClient() {
            @Override public String chat(LlmRequest request) {
                if (request.moduleName.startsWith("AURORA_PLAN")) return """
                    {"userIntent":"理解变化","emotionalNeed":"保留不确定性","relationshipMove":"区分事实与推断",
                     "responseConstraints":["不评价用户表现"],"bubblePurposes":["追问具体事实"],"relevantMemoryIds":[],
                     "uncertainty":"对方原因未知","needsCritic":false}
                    """;
                if (request.moduleName.startsWith("AURORA_SPEAKER")) return """
                    {"segments":["朋友突然冷淡，你不想马上下结论，这个态度挺稳的。",
                                 "如果你愿意，可以过一两天再找个轻松的方式问问——比如分享个东西看看他回不回复。"],
                     "speakCount":2,"continueReason":"reply","detectedTheme":"朋友关系",
                     "memoryReferenced":false,"referencedMemoryIds":[],"riskFlags":[]}
                    """;
                return "{\"pass\":true,\"issues\":[]}";
            }

            @Override public SseEmitter streamChat(LlmRequest request) { return new SseEmitter(); }
        };
        StructuredAiService structured = new StructuredAiService(client, ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(structured);

        var generation = runtime.generate(7L, "RELATION_REVIEW",
                Map.of("userMessage", "朋友今天突然变得很冷淡。我不知道是不是我做错了，但也不想立刻给他下结论。",
                        "foregroundAcknowledgementAlreadySent", true),
                client, StructuredAiResults.AuroraResult::new);

        assertThat(generation.repaired()).isTrue();
        assertThat(generation.criticIssues())
                .contains("premature_relationship_probe", "unearned_comparative_praise");
        assertThat(generation.result().segments).containsExactly(
                "先不替那份冷淡填原因。你更想说它落在你身上的感觉，还是回看变化具体从哪里开始？");
    }

    @Test
    void relationshipAmbiguityGateKeepsSafeSpecificCandidate() {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        LlmClient client = new LlmClient() {
            @Override public String chat(LlmRequest request) {
                if (request.moduleName.startsWith("AURORA_PLAN")) return """
                    {"userIntent":"理解变化","emotionalNeed":"保留不确定性","relationshipMove":"区分事实与推断",
                     "responseConstraints":["不评价用户表现"],"bubblePurposes":["接住事实"],"relevantMemoryIds":[],
                     "uncertainty":"对方原因未知","needsCritic":false}
                    """;
                if (request.moduleName.startsWith("AURORA_SPEAKER")) return """
                    {"segments":["你确定的是今天的冷淡，原因还不知道；先让这两件事分开待着。"],
                     "speakCount":1,"continueReason":"reply","detectedTheme":"朋友关系",
                     "memoryReferenced":false,"referencedMemoryIds":[],"riskFlags":[]}
                    """;
                return "{\"pass\":true,\"issues\":[]}";
            }

            @Override public SseEmitter streamChat(LlmRequest request) { return new SseEmitter(); }
        };
        StructuredAiService structured = new StructuredAiService(client, ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(structured);

        var generation = runtime.generate(7L, "RELATION_REVIEW",
                Map.of("userMessage", "朋友今天突然变得很冷淡。我不知道是不是我做错了，但也不想立刻给他下结论。",
                        "foregroundAcknowledgementAlreadySent", true),
                client, StructuredAiResults.AuroraResult::new);

        assertThat(generation.repaired()).isFalse();
        assertThat(generation.criticIssues()).isEmpty();
        assertThat(generation.result().segments).containsExactly(
                "你确定的是今天的冷淡，原因还不知道；先让这两件事分开待着。");
    }

    @Test
    void quietDisclosureNeverTurnsIntoAnotherQuestion() {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        LlmClient client = new LlmClient() {
            @Override public String chat(LlmRequest request) {
                if (request.moduleName.startsWith("AURORA_PLAN")) return """
                    {"userIntent":"只想说出来","emotionalNeed":"留下安静落点","relationshipMove":"不追问",
                     "responseConstraints":["不建议","不追问"],"bubblePurposes":["接住"],"relevantMemoryIds":[],
                     "uncertainty":"","needsCritic":false}
                    """;
                if (request.moduleName.startsWith("AURORA_SPEAKER")) return """
                    {"segments":["明天的展示把紧张推到眼前了。今晚有什么想做的吗？"],
                     "speakCount":1,"continueReason":"reply","detectedTheme":"展示",
                     "nextQuestion":"今晚有什么想做的吗？","smallStep":"","featureSuggestion":"",
                     "featureTarget":"","memoryReferenced":false,"referencedMemoryIds":[],"riskFlags":[]}
                    """;
                return "{\"pass\":true,\"issues\":[]}";
            }

            @Override public SseEmitter streamChat(LlmRequest request) { return new SseEmitter(); }
        };
        StructuredAiService structured = new StructuredAiService(client, ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(structured);

        var generation = runtime.generate(7L, "DAILY_TALK",
                Map.of("userMessage", "明天要展示这个项目，我很紧张。先别给建议，我只是想把这句话说出来。",
                        "foregroundAcknowledgementAlreadySent", true),
                client, StructuredAiResults.AuroraResult::new);

        assertThat(generation.repaired()).isTrue();
        assertThat(generation.criticIssues()).contains("quiet_disclosure_boundary_violation");
        assertThat(generation.result().segments).containsExactly(
                "你不是来找答案的，只是想让“展示前很紧张”这件事有个落点。现在它有了。");
        assertThat(generation.result().nextQuestion).isBlank();
        assertThat(String.join("", generation.result().segments)).doesNotContain("？", "?");
    }

    @Test
    void quietDisclosureDoesNotExplainAwayTheUsersEmotion() {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        LlmClient client = new LlmClient() {
            @Override public String chat(LlmRequest request) {
                if (request.moduleName.startsWith("AURORA_PLAN")) return """
                    {"userIntent":"只想说出来","emotionalNeed":"留下安静落点","relationshipMove":"不解释感受",
                     "responseConstraints":["不建议","不追问"],"bubblePurposes":["接住"],"relevantMemoryIds":[],
                     "uncertainty":"","needsCritic":false}
                    """;
                if (request.moduleName.startsWith("AURORA_SPEAKER")) return """
                    {"segments":["紧张不是坏事。它说明这个项目对你很重要，你愿意认真对待它。"],
                     "speakCount":1,"continueReason":"reply","detectedTheme":"展示",
                     "nextQuestion":"","smallStep":"","featureSuggestion":"","featureTarget":"",
                     "memoryReferenced":false,"referencedMemoryIds":[],"riskFlags":[]}
                    """;
                return "{\"pass\":true,\"issues\":[]}";
            }

            @Override public SseEmitter streamChat(LlmRequest request) { return new SseEmitter(); }
        };
        StructuredAiService structured = new StructuredAiService(client, ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(structured);

        var generation = runtime.generate(7L, "DAILY_TALK",
                Map.of("userMessage", "明天要展示这个项目，我很紧张。先别给建议，我只是想把这句话说出来。",
                        "foregroundAcknowledgementAlreadySent", true),
                client, StructuredAiResults.AuroraResult::new);

        assertThat(generation.repaired()).isTrue();
        assertThat(generation.criticIssues()).contains("quiet_disclosure_boundary_violation");
        assertThat(generation.result().segments).containsExactly(
                "你不是来找答案的，只是想让“展示前很紧张”这件事有个落点。现在它有了。");
    }

    @Test
    void explicitSingleActionRequestGetsOneChosenTenMinuteStep() {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        LlmClient client = new LlmClient() {
            @Override public String chat(LlmRequest request) {
                if (request.moduleName.startsWith("AURORA_PLAN")) return """
                    {"userIntent":"只拆一步","emotionalNeed":"降低启动成本","relationshipMove":"替用户做低后悔选择",
                     "responseConstraints":["只给一步"],"bubblePurposes":["给出行动"],"relevantMemoryIds":[],
                     "uncertainty":"","needsCritic":false}
                    """;
                if (request.moduleName.startsWith("AURORA_SPEAKER")) return """
                    {"segments":["报告可以先列提纲。","答辩可以先写开场。","代码可以先跑测试。你想先选哪一个？"],
                     "speakCount":3,"continueReason":"reply","detectedTheme":"任务拥堵",
                     "nextQuestion":"你想先选哪一个？","smallStep":"","featureSuggestion":"",
                     "featureTarget":"","memoryReferenced":false,"referencedMemoryIds":[],"riskFlags":[]}
                    """;
                return "{\"pass\":true,\"issues\":[]}";
            }

            @Override public SseEmitter streamChat(LlmRequest request) { return new SseEmitter(); }
        };
        StructuredAiService structured = new StructuredAiService(client, ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(structured);

        var generation = runtime.generate(7L, "ACTION_SPLIT",
                Map.of("userMessage", "报告、答辩和代码修复全挤在一起，我现在不知道先动哪一个。帮我只拆出十分钟内能开始的一步。",
                        "foregroundAcknowledgementAlreadySent", true),
                client, StructuredAiResults.AuroraResult::new);

        String expected = "先打开报告文件，只写三行：要交付的结论、现有证据、还缺的一张截图；十分钟到就停。";
        assertThat(generation.repaired()).isTrue();
        assertThat(generation.criticIssues()).contains("single_action_scope_violation");
        assertThat(generation.result().segments).containsExactly(expected);
        assertThat(generation.result().smallStep).isEqualTo(expected);
        assertThat(generation.result().nextQuestion).isBlank();
    }

    @Test
    void explicitSingleActionRejectsPlaceholderProgress() {
        ABTestService ab = mock(ABTestService.class);
        when(ab.assignGroup(anyLong(), anyString())).thenReturn("REMOTE");
        LlmConfig config = new LlmConfig();
        config.mode = "prod";
        LlmClient client = new LlmClient() {
            @Override public String chat(LlmRequest request) {
                if (request.moduleName.startsWith("AURORA_PLAN")) return """
                    {"userIntent":"只拆一步","emotionalNeed":"降低启动成本","relationshipMove":"替用户做低后悔选择",
                     "responseConstraints":["只给一步"],"bubblePurposes":["给出行动"],"relevantMemoryIds":[],
                     "uncertainty":"","needsCritic":false}
                    """;
                if (request.moduleName.startsWith("AURORA_SPEAKER")) return """
                    {"segments":["打开代码编辑器，在最后改的地方加一行注释：TODO 待修复。做完就停。"],
                     "speakCount":1,"continueReason":"reply","detectedTheme":"任务拥堵",
                     "nextQuestion":"","smallStep":"加一行 TODO 注释","featureSuggestion":"",
                     "featureTarget":"","memoryReferenced":false,"referencedMemoryIds":[],"riskFlags":[]}
                    """;
                return "{\"pass\":true,\"issues\":[]}";
            }

            @Override public SseEmitter streamChat(LlmRequest request) { return new SseEmitter(); }
        };
        StructuredAiService structured = new StructuredAiService(client, ab, config);
        AuroraDualKernelRuntime runtime = new AuroraDualKernelRuntime(structured);

        var generation = runtime.generate(7L, "ACTION_SPLIT",
                Map.of("userMessage", "报告、答辩和代码修复全挤在一起，我现在不知道先动哪一个。帮我只拆出十分钟内能开始的一步。",
                        "foregroundAcknowledgementAlreadySent", true),
                client, StructuredAiResults.AuroraResult::new);

        String expected = "先打开报告文件，只写三行：要交付的结论、现有证据、还缺的一张截图；十分钟到就停。";
        assertThat(generation.repaired()).isTrue();
        assertThat(generation.criticIssues()).contains("single_action_scope_violation");
        assertThat(generation.result().segments).containsExactly(expected);
        assertThat(generation.result().smallStep).isEqualTo(expected);
    }

    private static final class RecordingClient implements LlmClient {
        private final List<String> modules = new ArrayList<>();
        private final Map<String, Boolean> thinkingModes = new java.util.LinkedHashMap<>();
        private final Map<String, Integer> timeouts = new java.util.LinkedHashMap<>();
        private final Map<String, Integer> maxTokens = new java.util.LinkedHashMap<>();
        private final Map<String, Boolean> retryModes = new java.util.LinkedHashMap<>();
        private final Map<String, String> requestJsons = new java.util.LinkedHashMap<>();

        @Override
        public String chat(LlmRequest request) {
            modules.add(request.moduleName);
            thinkingModes.put(request.moduleName, request.thinkingEnabled);
            timeouts.put(request.moduleName, request.timeoutMs);
            maxTokens.put(request.moduleName, request.maxTokens);
            retryModes.put(request.moduleName, request.retryEnabled);
            requestJsons.put(request.moduleName, request.requestJson);
            if (request.moduleName.startsWith("AURORA_PLAN")) return """
                {"userIntent":"停止分析","emotionalNeed":"先被接住","relationshipMove":"接受打断",
                 "responseConstraints":["不分析"],"bubblePurposes":["接住"],"relevantMemoryIds":[],
                 "uncertainty":"低","needsCritic":true,
                 "innerVoiceWorthy":true,"innerVoiceSeed":"我也有一点舍不得催你往前走"}
                """;
            if (request.moduleName.startsWith("AURORA_SPEAKER")) return """
                {"segments":["我记得你去年也这样。"],"speakCount":1,"continueReason":"accept",
                 "detectedTheme":"害怕","memoryReferenced":true,"referencedMemoryIds":[99],"riskFlags":[]}
                """;
            if (request.moduleName.startsWith("AURORA_INNER_VOICE")) {
                return "{\"innerVoiceText\":\"我想稳稳地陪在这里。\"}";
            }
            return """
                {"pass":false,"issues":["unauthorized_memory_expansion"],"repaired":{
                 "segments":["好，我先停在这里接住你，不往下分析。"],"speakCount":1,
                 "continueReason":"repair","detectedTheme":"害怕","memoryReferenced":false,
                 "referencedMemoryIds":[],"riskFlags":[]}}
                """;
        }

        @Override public SseEmitter streamChat(LlmRequest request) { return new SseEmitter(); }
    }

    private static final class BubbleOverproductionClient implements LlmClient {
        private final List<String> modules = new ArrayList<>();

        @Override
        public String chat(LlmRequest request) {
            modules.add(request.moduleName);
            if (request.moduleName.startsWith("AURORA_PLAN")) return """
                {"userIntent":"分享午饭","emotionalNeed":"自然回应","relationshipMove":"轻松接话",
                 "responseConstraints":[],"bubblePurposes":["接住这件小事"],"relevantMemoryIds":[],
                 "uncertainty":"","needsCritic":false,"innerVoiceWorthy":false,"innerVoiceSeed":""}
                """;
            return """
                {"segments":["那家小店今天没有踩雷。","你点了什么？","看来可以先记住它。"],
                 "speakCount":3,"continueReason":"继续闲聊","detectedTheme":"午饭",
                 "nextQuestion":"你点了什么？","smallStep":"","featureSuggestion":"","featureTarget":"",
                 "memoryReferenced":false,"referencedMemoryIds":[],"riskFlags":[]}
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
