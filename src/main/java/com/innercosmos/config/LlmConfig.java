package com.innercosmos.config;

import com.innercosmos.ai.client.*;
import com.innercosmos.service.AiLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.ArrayList;
import java.util.List;

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
    public String asrProvider = "mimo";
    public GlmProperties glm = new GlmProperties();
    public MimoProperties mimo = new MimoProperties();
    public MinimaxProperties minimax = new MinimaxProperties();
    public DeepSeekProperties deepseek = new DeepSeekProperties();
    public String failoverProviders = "minimax,mimo,glm,deepseek";

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

    public String getAsrProvider() {
        return asrProvider;
    }

    public void setAsrProvider(String asrProvider) {
        this.asrProvider = asrProvider;
    }

    public String getFailoverProviders() {
        return failoverProviders;
    }

    public void setFailoverProviders(String failoverProviders) {
        this.failoverProviders = failoverProviders;
    }

    public String activeProvider() {
        return (provider != null && !provider.isBlank()) ? provider : "minimax";
    }

    public String activeModel() {
        String activeProvider = activeProvider().toLowerCase();
        if ("minimax".equals(activeProvider)) return minimax.model;
        if ("deepseek".equals(activeProvider)) return deepseek.model;
        if ("glm".equals(activeProvider)) return glm.model;
        if ("mimo".equals(activeProvider)) return mimo.model;
        return model;
    }

    public boolean hasActiveApiKey() {
        String activeProvider = activeProvider().toLowerCase();
        if ("mock".equals(activeProvider)) return false;
        if ("minimax".equals(activeProvider)) return !resolveKey(minimax.apiKey).isBlank();
        if ("deepseek".equals(activeProvider)) return !resolveKey(deepseek.apiKey).isBlank();
        if ("glm".equals(activeProvider)) return !resolveKey(glm.apiKey).isBlank();
        if ("mimo".equals(activeProvider)) return !resolveKey(mimo.apiKey).isBlank();
        return !resolveKey(apiKey).isBlank();
    }

    public String activeAsrProvider() {
        return (asrProvider != null && !asrProvider.isBlank()) ? asrProvider : "mimo";
    }

    public String activeAsrModel() {
        String provider = activeAsrProvider().toLowerCase();
        if ("mimo".equals(provider)) return mimo.asrModel;
        if ("glm".equals(provider)) return glm.asrModel;
        return "mock-asr";
    }

    public boolean hasActiveAsrKey() {
        String provider = activeAsrProvider().toLowerCase();
        if ("mock".equals(provider)) return false;
        if ("mimo".equals(provider)) return !resolveKey(mimo.apiKey).isBlank();
        if ("glm".equals(provider)) return !resolveKey(glm.asrApiKey).isBlank() || !resolveKey(glm.apiKey).isBlank();
        return false;
    }

    public GlmProperties getGlm() {
        return glm;
    }

    public void setGlm(GlmProperties glm) {
        this.glm = glm;
    }

    public MimoProperties getMimo() {
        return mimo;
    }

    public void setMimo(MimoProperties mimo) {
        this.mimo = mimo;
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
        public String asrModel = "glm-asr-2512";
        public String asrBaseUrl = "https://open.bigmodel.cn/api/paas/v4/audio/transcriptions";

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
        public String getAsrModel() { return asrModel; }
        public void setAsrModel(String asrModel) { this.asrModel = asrModel; }
        public String getAsrBaseUrl() { return asrBaseUrl; }
        public void setAsrBaseUrl(String asrBaseUrl) { this.asrBaseUrl = asrBaseUrl; }
    }

    public static class MimoProperties {
        public String apiKey = "";
        public String model = "mimo-v2.5";
        public String baseUrl = "https://api.xiaomimimo.com/v1/chat/completions";
        public String asrModel = "mimo-v2.5-asr";
        public String asrBaseUrl = "https://token-plan-cn.xiaomimimo.com/v1";
        public String asrLanguage = "auto";
        public int timeoutMs = 30000;

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getAsrModel() { return asrModel; }
        public void setAsrModel(String asrModel) { this.asrModel = asrModel; }
        public String getAsrBaseUrl() { return asrBaseUrl; }
        public void setAsrBaseUrl(String asrBaseUrl) { this.asrBaseUrl = asrBaseUrl; }
        public String getAsrLanguage() { return asrLanguage; }
        public void setAsrLanguage(String asrLanguage) { this.asrLanguage = asrLanguage; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    }

    public static class MinimaxProperties {
        public String apiKey = "";
        public String model = "MiniMax-M3";
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
        String minimaxKey = resolveKey(minimax.apiKey);
        String mimoKey = resolveKey(mimo.apiKey);
        String glmKey = resolveKey(glm.apiKey);
        String deepseekKey = resolveKey(deepseek.apiKey);
        log.info("Resolved LLM keys: minimax={}, mimo={}, glm={}, deepseek={}, topLevelApiKey={}",
                mask(minimaxKey), mask(mimoKey), mask(glmKey), mask(deepseekKey), mask(apiKey));
        if ("mock".equalsIgnoreCase(activeProvider)) {
            actualClient = new MockLlmClient(aiExecutor);
        } else if (isProdMode()) {
            actualClient = failoverClient(activeProvider, aiLogService, aiExecutor);
        } else {
            switch (activeProvider.toLowerCase()) {
            case "glm":
                actualClient = new GlmLlmClient(
                        resolveKey(glm.apiKey),
                        glm.baseUrl,
                        glm.model,
                        glm.timeoutMs,
                        isEffectiveFallbackAllowed(),
                        "GLM",
                        aiLogService,
                        aiExecutor
                );
                break;
            case "mimo":
                actualClient = new GlmLlmClient(
                        resolveKey(mimo.apiKey),
                        mimo.baseUrl,
                        mimo.model,
                        mimo.timeoutMs,
                        isEffectiveFallbackAllowed(),
                        "MIMO",
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
                        isEffectiveFallbackAllowed(),
                        "OPENAI_COMPATIBLE",
                        aiLogService,
                        aiExecutor
                );
                break;
            case "mock":
            default:
                actualClient = new MockLlmClient(aiExecutor);
            }
        }

        // Wrap with A/B test handler
        return new ABTestLlmClientWrapper(actualClient, aiLogService, aiExecutor);
    }

    private LlmClient failoverClient(String activeProvider, AiLogService aiLogService, Executor aiExecutor) {
        List<String> orderedProviders = orderedProviders(activeProvider);
        List<FailoverLlmClient.ProviderCandidate> candidates = new ArrayList<>();
        for (String providerName : orderedProviders) {
            LlmClient client = createProviderClient(providerName, false, aiLogService, aiExecutor);
            if (client != null) {
                candidates.add(new FailoverLlmClient.ProviderCandidate(providerName.toUpperCase(), activeModelFor(providerName), client));
            }
        }
        // Final safety net for the degradation circuit: Mock is always available, so once a
        // real provider (GLM) is down the chain tries the next real one (MiniMax) and, only if
        // every real provider fails, the keyword-aware Mock — the user never sees a hard error.
        // Gated on allow-fallback so a strict deployment can opt out.
        if (allowFallback) {
            candidates.add(new FailoverLlmClient.ProviderCandidate("MOCK", "mock-inner-cosmos", new MockLlmClient(aiExecutor)));
        }
        return new FailoverLlmClient(candidates, aiExecutor);
    }

    public LlmClient createProviderClient(String providerName, boolean fallbackAllowed,
                                          AiLogService aiLogService, Executor aiExecutor) {
        return switch (providerName.toLowerCase()) {
            case "minimax" -> new MiniMaxLlmClient(resolveKey(minimax.apiKey), minimax.baseUrl, minimax.model,
                    minimax.timeoutMs, fallbackAllowed, aiLogService, aiExecutor);
            case "mimo" -> new GlmLlmClient(resolveKey(mimo.apiKey), mimo.baseUrl, mimo.model,
                    mimo.timeoutMs, fallbackAllowed, "MIMO", aiLogService, aiExecutor);
            case "glm" -> new GlmLlmClient(resolveKey(glm.apiKey), glm.baseUrl, glm.model,
                    glm.timeoutMs, fallbackAllowed, "GLM", aiLogService, aiExecutor);
            case "deepseek" -> new DeepSeekLlmClient(resolveKey(deepseek.apiKey), deepseek.baseUrl, deepseek.model,
                    deepseek.timeoutMs, fallbackAllowed, aiLogService, aiExecutor);
            default -> null;
        };
    }

    /**
     * Map of {@code providerName -> LlmClient} for the M6 model selector. Keys are upper-case
     * (MINIMAX, MIMO, GLM, DEEPSEEK, MOCK). The Map is intentionally a {@link LinkedHashMap}
     * so that iteration order matches the natural reading order in the UI.
     *
     * <p>The default {@link #llmClient} bean is left untouched and continues to be the
     * fallback used by callers that have not been wired through the model router.
     */
    @Bean(name = "namedLlmClients")
    public Map<String, LlmClient> namedLlmClients(AiLogService aiLogService, Executor aiExecutor) {
        Map<String, LlmClient> m = new LinkedHashMap<>();
        // M-006 (Phase-6 fix): wrap each routed client with the PII-redacting wrapper so the
        // model-router path (Aurora chat with a real provider active) redacts too — previously
        // these were raw clients, bypassing redaction entirely.
        // Only register providers that actually have an API key. Otherwise a stale per-user/
        // per-session preferred_model (e.g. a seeded "DEEPSEEK") would route real calls to a
        // keyless provider and 401 every time; with keyless providers absent from the map the
        // SessionModelRouter cleanly falls back to the system default (see resolve()).
        if (!resolveKey(minimax.apiKey).isBlank()) {
            m.put("MINIMAX", new ABTestLlmClientWrapper(createProviderClient("minimax", false, aiLogService, aiExecutor), aiLogService, aiExecutor));
        }
        if (!resolveKey(mimo.apiKey).isBlank()) {
            m.put("MIMO", new ABTestLlmClientWrapper(createProviderClient("mimo", false, aiLogService, aiExecutor), aiLogService, aiExecutor));
        }
        if (!resolveKey(glm.apiKey).isBlank()) {
            m.put("GLM", new ABTestLlmClientWrapper(createProviderClient("glm", false, aiLogService, aiExecutor), aiLogService, aiExecutor));
        }
        if (!resolveKey(deepseek.apiKey).isBlank()) {
            m.put("DEEPSEEK", new ABTestLlmClientWrapper(createProviderClient("deepseek", false, aiLogService, aiExecutor), aiLogService, aiExecutor));
        }
        m.put("MOCK", new MockLlmClient(aiExecutor));
        return m;
    }

    public List<String> orderedProviderNames() {
        return orderedProviders(activeProvider());
    }

    public List<String> orderedProviderModels() {
        return orderedProviderNames().stream()
                .map(providerName -> providerName.toUpperCase() + "/" + activeModelFor(providerName))
                .toList();
    }

    private List<String> orderedProviders(String activeProvider) {
        List<String> providers = new ArrayList<>();
        addProvider(providers, activeProvider);
        if (failoverProviders != null) {
            for (String item : failoverProviders.split(",")) {
                addProvider(providers, item);
            }
        }
        if (providers.isEmpty()) {
            providers.add("minimax");
        }
        return providers;
    }

    private void addProvider(List<String> providers, String provider) {
        if (provider == null || provider.isBlank() || "mock".equalsIgnoreCase(provider)) return;
        String normalized = provider.trim().toLowerCase();
        if (providers.stream().noneMatch(p -> p.equalsIgnoreCase(normalized))) {
            providers.add(normalized);
        }
    }

    private String activeModelFor(String providerName) {
        return switch (providerName.toLowerCase()) {
            case "minimax" -> minimax.model;
            case "mimo" -> mimo.model;
            case "glm" -> glm.model;
            case "deepseek" -> deepseek.model;
            default -> model;
        };
    }

    private String resolveKey(String key) {
        return (key != null && !key.isBlank()) ? key : (apiKey != null ? apiKey : "");
    }

    private String mask(String key) {
        if (key == null || key.isBlank()) return "<EMPTY>";
        if (key.length() <= 8) return "***";
        return key.substring(0, 4) + "***" + key.substring(key.length() - 4);
    }
}
