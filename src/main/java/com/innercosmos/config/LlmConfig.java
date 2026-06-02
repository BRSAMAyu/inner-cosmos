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
    public boolean allowFallback = true;
    public GlmProperties glm = new GlmProperties();
    public MinimaxProperties minimax = new MinimaxProperties();
    public DeepSeekProperties deepseek = new DeepSeekProperties();

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

    public boolean isAllowFallback() {
        return allowFallback;
    }

    public void setAllowFallback(boolean allowFallback) {
        this.allowFallback = allowFallback;
    }

    public boolean isProdMode() {
        return "prod".equalsIgnoreCase(mode) || "production".equalsIgnoreCase(mode);
    }

    public boolean isDemoMode() {
        return "demo".equalsIgnoreCase(mode) || "dev".equalsIgnoreCase(mode) || "local".equalsIgnoreCase(mode);
    }

    public boolean isEffectiveFallbackAllowed() {
        return allowFallback && !isProdMode();
    }

    public String activeProvider() {
        return (provider != null && !provider.isBlank()) ? provider : "minimax";
    }

    public String activeModel() {
        String activeProvider = activeProvider().toLowerCase();
        if ("minimax".equals(activeProvider)) return minimax.model;
        if ("deepseek".equals(activeProvider)) return deepseek.model;
        if ("glm".equals(activeProvider)) return glm.model;
        return model;
    }

    public boolean hasActiveApiKey() {
        String activeProvider = activeProvider().toLowerCase();
        if ("mock".equals(activeProvider)) return false;
        if ("minimax".equals(activeProvider)) return !resolveKey(minimax.apiKey).isBlank();
        if ("deepseek".equals(activeProvider)) return !resolveKey(deepseek.apiKey).isBlank();
        if ("glm".equals(activeProvider)) return !resolveKey(glm.apiKey).isBlank();
        return !resolveKey(apiKey).isBlank();
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

    public DeepSeekProperties getDeepseek() {
        return deepseek;
    }

    public void setDeepseek(DeepSeekProperties deepseek) {
        this.deepseek = deepseek;
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
        public String model = "MiniMax-M2.7";
        public String baseUrl = "https://api.minimaxi.com/v1/chat/completions";
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

    public static class DeepSeekProperties {
        public String apiKey = "";
        public String model = "deepseek-chat";
        public String baseUrl = "https://api.deepseek.com";
        public int timeoutMs = 30000;

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
        String activeProvider = activeProvider();
        log.info("Creating LlmClient for provider: {}, mode: {}, fallbackAllowed: {}",
                activeProvider, mode, isEffectiveFallbackAllowed());

        LlmClient actualClient;
        switch (activeProvider.toLowerCase()) {
            case "glm":
                actualClient = new GlmLlmClient(
                        resolveKey(glm.apiKey),
                        glm.baseUrl,
                        glm.model,
                        glm.timeoutMs,
                        aiLogService,
                        aiExecutor
                );
                break;
            case "minimax":
                actualClient = new MiniMaxLlmClient(
                        resolveKey(minimax.apiKey),
                        minimax.baseUrl,
                        minimax.model,
                        minimax.timeoutMs,
                        isEffectiveFallbackAllowed(),
                        aiLogService,
                        aiExecutor
                );
                break;
            case "deepseek":
                actualClient = new DeepSeekLlmClient(
                        resolveKey(deepseek.apiKey),
                        deepseek.baseUrl,
                        deepseek.model,
                        deepseek.timeoutMs,
                        isEffectiveFallbackAllowed(),
                        aiLogService,
                        aiExecutor
                );
                break;
            case "openai-compatible":
                actualClient = new GlmLlmClient(
                        resolveKey(apiKey),
                        baseUrl,
                        model,
                        20000,
                        aiLogService,
                        aiExecutor
                );
                break;
            case "mock":
            default:
                actualClient = new MockLlmClient(aiExecutor);
        }

        // Wrap with A/B test handler
        return new ABTestLlmClientWrapper(actualClient, aiLogService, aiExecutor);
    }

    private String resolveKey(String key) {
        return (key != null && !key.isBlank()) ? key : (apiKey != null ? apiKey : "");
    }
}
