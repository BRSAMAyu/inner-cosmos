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

    @Test
    void explicitEnglishTurnContractOverridesChineseDominatedStructuredPrompt() {
        CapturingClient delegate = new CapturingClient();
        PromptLanguageLlmClient client = new PromptLanguageLlmClient(delegate, "auto");
        LlmRequest request = new LlmRequest(1L, "AURORA_SPEAKER_DAILY_TALK",
                "Input JSON contains many Chinese mode labels and examples");
        request.requestJson = """
                {"userMessage":"I need to say this without advice.",
                 "outputLanguage":"en","modeGuide":"倾诉：优先回应用户"}
                """;
        request.systemPrompt = "你是 Aurora 的前台表达核。只输出严格 JSON。";

        client.chat(request);

        assertThat(delegate.request.systemPrompt)
                .contains("Output language is English")
                .contains("speaker")
                .contains("critic repairs")
                .contains("Do not switch to Chinese");
    }

    @Test
    void explicitChineseTurnContractOverridesEnglishDominatedStructuredPrompt() {
        CapturingClient delegate = new CapturingClient();
        PromptLanguageLlmClient client = new PromptLanguageLlmClient(delegate, "auto");
        LlmRequest request = new LlmRequest(1L, "AURORA_CRITIC_DAILY_TALK",
                "Input JSON with English schemas and instructions");
        request.requestJson = """
                {"userMessage":"我只想把这句话说出来。","outputLanguage":"zh-CN",
                 "modeGuide":"Respond naturally and return strict JSON"}
                """;
        request.systemPrompt = "Return JSON only.";

        client.chat(request);

        assertThat(delegate.request.systemPrompt)
                .contains("输出语言为简体中文")
                .contains("Speaker segments")
                .contains("critic 修复");
    }

    @Test
    void latestClearlyMonolingualMessageWinsOverStaleLocale() {
        assertThat(PromptLanguageLlmClient.normalizeOutputLanguage(
                "zh-CN", "I need one clear question, not a list."))
                .isEqualTo("en");
        assertThat(PromptLanguageLlmClient.normalizeOutputLanguage(
                "en-US", "我现在只想安静地把这句话说完。"))
                .isEqualTo("zh-CN");
    }

    @Test
    void explicitLocaleBreaksTieForGenuinelyMixedInput() {
        String mixed = "我想 review API design before 明天的 demo";

        assertThat(PromptLanguageLlmClient.normalizeOutputLanguage("en-US", mixed))
                .isEqualTo("en");
        assertThat(PromptLanguageLlmClient.normalizeOutputLanguage("zh-CN", mixed))
                .isEqualTo("zh-CN");
    }

    @Test
    void autoModeUsesDominantScriptForMixedInput() {
        assertThat(PromptLanguageLlmClient.normalizeOutputLanguage(
                null, "这次 API demo 我最担心的是现场恢复是否稳定。"))
                .isEqualTo("zh-CN");
        assertThat(PromptLanguageLlmClient.normalizeOutputLanguage(
                null, "I need the API demo to stay clear and stable 明天."))
                .isEqualTo("en");
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
