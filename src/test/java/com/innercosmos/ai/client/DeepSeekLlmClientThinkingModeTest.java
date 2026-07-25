package com.innercosmos.ai.client;

import com.innercosmos.service.AiLogService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DeepSeekLlmClientThinkingModeTest {

    private final Executor direct = Runnable::run;
    private final DeepSeekLlmClient client = new DeepSeekLlmClient(
            "test-key", "https://api.deepseek.com", "deepseek-v4-flash", 30000,
            false, mock(AiLogService.class), direct);

    @Test
    void foregroundSpeakerExplicitlyDisablesThinkingAndKeepsTemperature() {
        LlmRequest request = new LlmRequest(7L, "AURORA_SPEAKER_DAILY_TALK", "{}");
        request.thinkingEnabled = false;
        request.temperature = 0.55;

        Map<String, Object> body = client.requestBody(request,
                List.of(Map.of("role", "user", "content", "{}")), false);

        assertThat(body.get("thinking")).isEqualTo(Map.of("type", "disabled"));
        assertThat(body.get("temperature")).isEqualTo(0.55);
        assertThat(body.get("max_tokens")).isEqualTo(LlmClient.RESPONSE_MAX_TOKENS);
    }

    @Test
    void backgroundPlannerEnablesThinkingAndOmitsUnsupportedTemperature() {
        LlmRequest request = new LlmRequest(7L, "AURORA_PLAN_DAILY_TALK", "{}");
        request.thinkingEnabled = true;
        request.temperature = 0.55;

        Map<String, Object> body = client.requestBody(request,
                List.of(Map.of("role", "user", "content", "{}")), false);

        assertThat(body.get("thinking")).isEqualTo(Map.of("type", "enabled"));
        assertThat(body).doesNotContainKey("temperature");
    }

    @Test
    void structuredStageCanUseItsOwnBoundedOutputBudget() {
        LlmRequest request = new LlmRequest(7L, "AURORA_PLAN_DAILY_TALK", "{}");
        request.thinkingEnabled = true;
        request.maxTokens = 2048;

        Map<String, Object> body = client.requestBody(request,
                List.of(Map.of("role", "user", "content", "{}")), false);

        assertThat(body.get("max_tokens")).isEqualTo(2048);
    }
}
