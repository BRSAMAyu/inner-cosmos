package com.innercosmos.ai.client;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

class PromptLanguageLlmClientTest {
    @Test
    void appendsAutomaticUserLanguageBoundaryAfterLegacyChinesePromptAndOnlyOnce() {
        CapturingClient delegate = new CapturingClient();
        PromptLanguageLlmClient client = new PromptLanguageLlmClient(delegate, "auto");
        LlmRequest request = new LlmRequest(1L, "AURORA", "hello");
        request.systemPrompt = "请使用自然中文回答";

        client.chat(request);
        client.chat(request);

        assertThat(delegate.request.systemPrompt)
                .contains("请使用自然中文回答")
                .contains("Detect the dominant language")
                .contains("overrides older");
        assertThat(delegate.request.systemPrompt.split(
                java.util.regex.Pattern.quote(PromptLanguageLlmClient.MARKER), -1)).hasSize(2);
    }

    @Test
    void leavesChineseModeUnchanged() {
        CapturingClient delegate = new CapturingClient();
        PromptLanguageLlmClient client = new PromptLanguageLlmClient(delegate, "zh-CN");
        LlmRequest request = new LlmRequest(1L, "AURORA", "你好");
        request.systemPrompt = "中文";

        client.chat(request);

        assertThat(delegate.request.systemPrompt).isEqualTo("中文");
    }

    private static final class CapturingClient implements LlmClient {
        private LlmRequest request;
        @Override public String chat(LlmRequest request) {
            this.request = request;
            return "ok";
        }
        @Override public SseEmitter streamChat(LlmRequest request) {
            this.request = request;
            return new SseEmitter();
        }
    }
}
