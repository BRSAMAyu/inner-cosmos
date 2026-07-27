package com.innercosmos.ai.client;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

class AuroraStageRoutingLlmClientTest {

    @Test
    void routesFastSpeakerAndReflectiveStagesToDifferentClientsAndReasoningLevels() {
        CapturingClient fallback = new CapturingClient("fallback");
        CapturingClient fast = new CapturingClient("fast");
        CapturingClient speaker = new CapturingClient("speaker");
        CapturingClient thinker = new CapturingClient("thinker");
        AuroraStageRoutingLlmClient router =
                new AuroraStageRoutingLlmClient(fallback, fast, speaker, thinker);

        LlmRequest foreground = request("AURORA_FOREGROUND_DAILY_TALK");
        assertThat(router.chat(foreground)).isEqualTo("fast");
        assertThat(foreground.thinkingEnabled).isFalse();
        assertThat(foreground.reasoningEffort).isEqualTo("minimal");
        assertThat(foreground.temperature).isEqualTo(0.25);
        assertThat(foreground.maxTokens).isEqualTo(256);

        LlmRequest spoken = request("AURORA_SPEAKER_DAILY_TALK");
        assertThat(router.chat(spoken)).isEqualTo("speaker");
        assertThat(spoken.thinkingEnabled).isFalse();
        assertThat(spoken.reasoningEffort).isEqualTo("minimal");
        assertThat(spoken.temperature).isEqualTo(0.78);
        assertThat(spoken.maxTokens).isEqualTo(2_048);

        LlmRequest plan = request("AURORA_PLAN_DAILY_TALK");
        assertThat(router.chat(plan)).isEqualTo("thinker");
        assertThat(plan.thinkingEnabled).isTrue();
        assertThat(plan.reasoningEffort).isEqualTo("high");
        assertThat(plan.temperature).isEqualTo(0.10);
        assertThat(plan.maxTokens).isEqualTo(8_192);

        assertThat(router.chat(request("MEMORY_EXTRACT"))).isEqualTo("fallback");
    }

    private LlmRequest request(String module) {
        return new LlmRequest(1L, module, "{}");
    }

    private static final class CapturingClient implements LlmClient {
        private final String name;

        private CapturingClient(String name) {
            this.name = name;
        }

        @Override
        public String chat(LlmRequest request) {
            return name;
        }

        @Override
        public SseEmitter streamChat(LlmRequest request) {
            return new SseEmitter();
        }
    }
}
