package com.innercosmos.config;

import com.innercosmos.ai.client.*;
import com.innercosmos.service.AiLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;

@Configuration
@ConfigurationProperties(prefix = "llm")
public class LlmConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    public String mode;
    public String provider;
    public String apiKey;
    public String baseUrl;
    public String model;
    public GlmProperties glm = new GlmProperties();
    public MinimaxProperties minimax = new MinimaxProperties();

    // --- Getters / Setters for top-level fields ---

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public GlmProperties getGlm() {
        return glm;
    }

    public void setGlm(GlmProperties glm) {
        this.glm = glm;
    }

    public MinimaxProperties getMinimax() {
        return minimax;
    }

    public void setMinimax(MinimaxProperties minimax) {
        this.minimax = minimax;
    }

    // --- Nested property classes ---

    public static class GlmProperties {
        public String apiKey = "";
        public String model = "glm-4-flash";
        public String baseUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions";
        public int timeoutMs = 20000;
        public String asrApiKey = "";

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
        public String getAsrApiKey() { return asrApiKey; }
        public void setAsrApiKey(String asrApiKey) { this.asrApiKey = asrApiKey; }
    }

    public static class MinimaxProperties {
        public String apiKey = "";
        public String model = "MiniMax-Text-01";
        public String baseUrl = "https://api.minimax.chat/v1/text/chatcompletion_v2";
        public int timeoutMs = 20000;

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    }

    // --- Factory method ---

    @Bean
    public LlmClient llmClient(AiLogService aiLogService, Executor aiExecutor) {
        String activeProvider = (provider != null && !provider.isBlank()) ? provider : "mock";
        log.info("Creating LlmClient for provider: {}", activeProvider);

        switch (activeProvider.toLowerCase()) {
            case "glm":
                return new GlmLlmClient(
                        resolveKey(glm.apiKey),
                        glm.baseUrl,
                        glm.model,
                        glm.timeoutMs,
                        aiLogService,
                        aiExecutor
                );
            case "minimax":
                return new MiniMaxLlmClient(
                        resolveKey(minimax.apiKey),
                        minimax.baseUrl,
                        minimax.model,
                        minimax.timeoutMs,
                        aiLogService,
                        aiExecutor
                );
            case "deepseek":
                return new DeepSeekLlmClient();
            case "openai-compatible":
                return new GlmLlmClient(
                        resolveKey(apiKey),
                        baseUrl,
                        model,
                        20000,
                        aiLogService,
                        aiExecutor
                );
            case "mock":
            default:
                return new MockLlmClient(aiExecutor);
        }
    }

    private String resolveKey(String key) {
        return (key != null && !key.isBlank()) ? key : (apiKey != null ? apiKey : "");
    }
}
