package com.innercosmos.ai.client;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class FailoverLlmClientTest {

    @Test
    void movesToNextRealProviderAndRestoresStageTimeout() {
        AtomicInteger primaryCalls = new AtomicInteger();
        AtomicInteger secondaryCalls = new AtomicInteger();
        LlmClient primary = client(request -> {
            primaryCalls.incrementAndGet();
            throw new RuntimeException("429 rate limited");
        });
        LlmClient secondary = client(request -> {
            secondaryCalls.incrementAndGet();
            assertThat(request.timeoutMs).isPositive().isLessThanOrEqualTo(5_000);
            return "real secondary response";
        });
        FailoverLlmClient failover = new FailoverLlmClient(List.of(
                new FailoverLlmClient.ProviderCandidate("GEMINI", "gemini-3.6-flash", primary),
                new FailoverLlmClient.ProviderCandidate("DEEPSEEK", "deepseek-v4-flash", secondary)
        ), Runnable::run);
        LlmRequest request = new LlmRequest(7L, "AURORA_SPEAKER_TEST", "prompt");
        request.timeoutMs = 5_000;
        request.totalTimeoutMs = 12_000;

        assertThat(failover.chat(request)).isEqualTo("real secondary response");
        assertThat(primaryCalls).hasValue(1);
        assertThat(secondaryCalls).hasValue(1);
        assertThat(request.timeoutMs).isEqualTo(5_000);
    }

    private static LlmClient client(ChatCall call) {
        return new LlmClient() {
            @Override
            public String chat(LlmRequest request) {
                return call.chat(request);
            }

            @Override
            public SseEmitter streamChat(LlmRequest request) {
                return new SseEmitter();
            }
        };
    }

    @FunctionalInterface
    private interface ChatCall {
        String chat(LlmRequest request);
    }
}
