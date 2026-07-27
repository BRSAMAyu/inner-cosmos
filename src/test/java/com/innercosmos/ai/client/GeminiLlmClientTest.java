package com.innercosmos.ai.client;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GeminiLlmClientTest {

    @Test
    void emitsNativeGeminiThinkingConfigAndNeverPlacesKeyInBody() {
        GeminiLlmClient client = new GeminiLlmClient(
                "secret-that-must-not-enter-json",
                "https://generativelanguage.googleapis.com/v1beta",
                "gemini-3.6-flash", "medium", 30_000,
                false, mock(com.innercosmos.service.AiLogService.class), directExecutor());
        LlmRequest request = new LlmRequest(7L, "AURORA_SPEAKER_DAILY_TALK", "{\"x\":1}");
        request.systemPrompt = "system";
        request.thinkingEnabled = true;
        request.reasoningEffort = "high";
        request.maxTokens = 6_144;

        Map<String, Object> body = client.requestBody(request);

        @SuppressWarnings("unchecked")
        Map<String, Object> generation = (Map<String, Object>) body.get("generationConfig");
        @SuppressWarnings("unchecked")
        Map<String, Object> thinking = (Map<String, Object>) generation.get("thinkingConfig");
        assertThat(thinking).containsEntry("thinkingLevel", "high");
        assertThat(generation).containsEntry("maxOutputTokens", 6_144);
        assertThat(body.toString()).doesNotContain("secret-that-must-not-enter-json");
        assertThat(body).doesNotContainKeys("temperature", "topP", "topK");
    }

    @Test
    void mapsNonThinkingFastKernelToMinimal() {
        GeminiLlmClient client = new GeminiLlmClient(
                "key", null, "gemini-3.5-flash-lite", "medium",
                30_000, false, null, directExecutor());
        LlmRequest request = new LlmRequest(7L, "AURORA_FOREGROUND_DAILY_TALK", "{}");
        request.thinkingEnabled = false;

        @SuppressWarnings("unchecked")
        Map<String, Object> generation =
                (Map<String, Object>) client.requestBody(request).get("generationConfig");
        @SuppressWarnings("unchecked")
        Map<String, Object> thinking = (Map<String, Object>) generation.get("thinkingConfig");
        assertThat(thinking).containsEntry("thinkingLevel", "minimal");
    }

    private Executor directExecutor() {
        return Runnable::run;
    }
}
