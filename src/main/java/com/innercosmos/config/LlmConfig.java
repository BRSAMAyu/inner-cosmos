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
    public PromptProperties prompt = new PromptProperties();
    public boolean allowFallback = true;
    public String asrProvider = "mimo";
    public GlmProperties glm = new GlmProperties();
    public MimoProperties mimo = new MimoProperties();
    public MinimaxProperties minimax = new MinimaxProperties();
    public DeepSeekProperties deepseek = new DeepSeekProperties();
    public GeminiProperties gemini = new GeminiProperties();
    public AuroraStageProperties auroraStages = new AuroraStageProperties();
    public ContextProperties context = new ContextProperties();
    public String failoverProviders = "gemini,minimax,mimo,glm,deepseek";

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

    public PromptProperties getPrompt() {
        return prompt;
    }

    public void setPrompt(PromptProperties prompt) {
        this.prompt = prompt;
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
        if ("gemini".equals(activeProvider)) return gemini.model;
        if ("glm".equals(activeProvider)) return glm.model;
        if ("mimo".equals(activeProvider)) return mimo.model;
        return model;
    }

    public boolean hasActiveApiKey() {
        String activeProvider = activeProvider().toLowerCase();
        if ("mock".equals(activeProvider)) return false;
        if ("minimax".equals(activeProvider)) return !resolveKey(minimax.apiKey).isBlank();
        if ("deepseek".equals(activeProvider)) return !resolveKey(deepseek.apiKey).isBlank();
        if ("gemini".equals(activeProvider)) return !resolveKey(gemini.apiKey).isBlank();
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

    public GeminiProperties getGemini() {
        return gemini;
    }

    public void setGemini(GeminiProperties gemini) {
        this.gemini = gemini;
    }

    public AuroraStageProperties getAuroraStages() {
        return auroraStages;
    }

    public void setAuroraStages(AuroraStageProperties auroraStages) {
        this.auroraStages = auroraStages;
    }

    public ContextProperties getContext() {
        return context;
    }

    public void setContext(ContextProperties context) {
        this.context = context;
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
        public String model = "deepseek-v4-flash";
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

    public static class PromptProperties {
        public String language = "auto";

        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
    }

    public static class GeminiProperties {
        public String apiKey = "";
        public String model = "gemini-3.6-flash";
        public String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
        public String thinkingLevel = "medium";
        public int timeoutMs = 30000;

        public String getApiKey() { return apiKey; }
        public void setApiKey(String value) { this.apiKey = value; }
        public String getModel() { return model; }
        public void setModel(String value) { this.model = value; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String value) { this.baseUrl = value; }
        public String getThinkingLevel() { return thinkingLevel; }
        public void setThinkingLevel(String value) { this.thinkingLevel = value; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int value) { this.timeoutMs = value; }
    }

    /**
     * Aurora's three temporal layers. Credentials remain provider-owned fields; this block
     * only selects model identities and reasoning levels.
     */
    public static class AuroraStageProperties {
        public boolean enabled = true;
        public String fastModel = "gemini-3.5-flash-lite";
        public String speakerModel = "gemini-3.6-flash";
        public String thinkerModel = "deepseek-v4-pro";
        public String speakerThinkingLevel = "medium";
        public String thinkerReasoningEffort = "high";
        public double fastTemperature = 0.25;
        public double speakerTemperature = 0.82;
        public double thinkerTemperature = 0.10;
        public double criticTemperature = 0.05;
        public int fastMaxTokens = 256;
        public int speakerMaxTokens = 6_144;
        public int thinkerMaxTokens = 8_192;
        public int criticMaxTokens = 2_048;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { this.enabled = value; }
        public String getFastModel() { return fastModel; }
        public void setFastModel(String value) { this.fastModel = value; }
        public String getSpeakerModel() { return speakerModel; }
        public void setSpeakerModel(String value) { this.speakerModel = value; }
        public String getThinkerModel() { return thinkerModel; }
        public void setThinkerModel(String value) { this.thinkerModel = value; }
        public String getSpeakerThinkingLevel() { return speakerThinkingLevel; }
        public void setSpeakerThinkingLevel(String value) { this.speakerThinkingLevel = value; }
        public String getThinkerReasoningEffort() { return thinkerReasoningEffort; }
        public void setThinkerReasoningEffort(String value) { this.thinkerReasoningEffort = value; }
        public double getFastTemperature() { return fastTemperature; }
        public void setFastTemperature(double value) { this.fastTemperature = value; }
        public double getSpeakerTemperature() { return speakerTemperature; }
        public void setSpeakerTemperature(double value) { this.speakerTemperature = value; }
        public double getThinkerTemperature() { return thinkerTemperature; }
        public void setThinkerTemperature(double value) { this.thinkerTemperature = value; }
        public double getCriticTemperature() { return criticTemperature; }
        public void setCriticTemperature(double value) { this.criticTemperature = value; }
        public int getFastMaxTokens() { return fastMaxTokens; }
        public void setFastMaxTokens(int value) { this.fastMaxTokens = value; }
        public int getSpeakerMaxTokens() { return speakerMaxTokens; }
        public void setSpeakerMaxTokens(int value) { this.speakerMaxTokens = value; }
        public int getThinkerMaxTokens() { return thinkerMaxTokens; }
        public void setThinkerMaxTokens(int value) { this.thinkerMaxTokens = value; }
        public int getCriticMaxTokens() { return criticMaxTokens; }
        public void setCriticMaxTokens(int value) { this.criticMaxTokens = value; }
    }

    /**
     * Aurora's input window policy. Provider windows include both input and output; the
     * effective input cap therefore always subtracts the response reserve and safety margin.
     */
    public static class ContextProperties {
        public int hardMaxInputTokens = 200_000;
        public int outputReserveTokens = 4_096;
        public int safetyMarginTokens = 4_096;
        public int defaultProviderWindowTokens = 128_000;
        public int openingAnchorTokens = 2_048;
        public int criticalAnchorTokens = 4_096;
        public Map<String, Integer> providerWindowTokens = new LinkedHashMap<>(Map.of(
                "deepseek", 1_000_000,
                "glm", 200_000,
                "mimo", 1_000_000,
                "minimax", 204_800,
                "gemini", 1_048_576,
                "mock", 200_000
        ));

        public int getHardMaxInputTokens() { return hardMaxInputTokens; }
        public void setHardMaxInputTokens(int value) { this.hardMaxInputTokens = value; }
        public int getOutputReserveTokens() { return outputReserveTokens; }
        public void setOutputReserveTokens(int value) { this.outputReserveTokens = value; }
        public int getSafetyMarginTokens() { return safetyMarginTokens; }
        public void setSafetyMarginTokens(int value) { this.safetyMarginTokens = value; }
        public int getDefaultProviderWindowTokens() { return defaultProviderWindowTokens; }
        public void setDefaultProviderWindowTokens(int value) { this.defaultProviderWindowTokens = value; }
        public int getOpeningAnchorTokens() { return openingAnchorTokens; }
        public void setOpeningAnchorTokens(int value) { this.openingAnchorTokens = value; }
        public int getCriticalAnchorTokens() { return criticalAnchorTokens; }
        public void setCriticalAnchorTokens(int value) { this.criticalAnchorTokens = value; }
        public Map<String, Integer> getProviderWindowTokens() { return providerWindowTokens; }
        public void setProviderWindowTokens(Map<String, Integer> value) {
            this.providerWindowTokens = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
        }

        public int providerWindow(String provider) {
            String key = provider == null ? "" : provider.trim().toLowerCase(java.util.Locale.ROOT);
            return Math.max(8_192, providerWindowTokens.getOrDefault(key, defaultProviderWindowTokens));
        }
    }

    // --- Factory method ---

    @Bean
    public LlmClient llmClient(AiLogService aiLogService, Executor aiExecutor) {
        String activeProvider = activeProvider();
        log.info("Creating LlmClient for provider: {}, mode: {}, fallbackAllowed: {}",
                activeProvider, mode, isEffectiveFallbackAllowed());

        LlmClient actualClient;
        String minimaxKey = providerKey("minimax", minimax.apiKey);
        String mimoKey = providerKey("mimo", mimo.apiKey);
        String glmKey = providerKey("glm", glm.apiKey);
        String deepseekKey = providerKey("deepseek", deepseek.apiKey);
        String geminiKey = providerKey("gemini", gemini.apiKey);
        log.info("Resolved LLM credential configuration: minimax={}, mimo={}, glm={}, deepseek={}, gemini={}, topLevelApiKey={}",
                configured(minimaxKey), configured(mimoKey), configured(glmKey),
                configured(deepseekKey), configured(geminiKey), configured(apiKey));
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
            case "gemini":
                actualClient = new GeminiLlmClient(
                        resolveKey(gemini.apiKey),
                        gemini.baseUrl,
                        gemini.model,
                        gemini.thinkingLevel,
                        gemini.timeoutMs,
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
        return languageAware(new ABTestLlmClientWrapper(
                auroraStageRouter(actualClient, aiLogService, aiExecutor),
                aiLogService, aiExecutor));
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
        // Gated on the EFFECTIVE allow-fallback (not the raw field): this method is only ever
        // invoked from the isProdMode() branch, so using the raw flag here would silently wire
        // Mock into the failover chain if llm.mode=prod is ever set without the `prod` Spring
        // profile active (2026-07-24 8-agent audit P2-3) -- every other construction path in this
        // class already uses the effective check.
        if (isEffectiveFallbackAllowed()) {
            candidates.add(new FailoverLlmClient.ProviderCandidate("MOCK", "mock-inner-cosmos", new MockLlmClient(aiExecutor)));
        }
        return new FailoverLlmClient(candidates, aiExecutor);
    }

    public LlmClient createProviderClient(String providerName, boolean fallbackAllowed,
                                          AiLogService aiLogService, Executor aiExecutor) {
        return switch (providerName.toLowerCase()) {
            case "minimax" -> new MiniMaxLlmClient(providerKey("minimax", minimax.apiKey), minimax.baseUrl, minimax.model,
                    minimax.timeoutMs, fallbackAllowed, aiLogService, aiExecutor);
            case "mimo" -> new GlmLlmClient(providerKey("mimo", mimo.apiKey), mimo.baseUrl, mimo.model,
                    mimo.timeoutMs, fallbackAllowed, "MIMO", aiLogService, aiExecutor);
            case "glm" -> new GlmLlmClient(providerKey("glm", glm.apiKey), glm.baseUrl, glm.model,
                    glm.timeoutMs, fallbackAllowed, "GLM", aiLogService, aiExecutor);
            case "deepseek" -> new DeepSeekLlmClient(providerKey("deepseek", deepseek.apiKey), deepseek.baseUrl, deepseek.model,
                    deepseek.timeoutMs, fallbackAllowed, aiLogService, aiExecutor);
            case "gemini" -> new GeminiLlmClient(providerKey("gemini", gemini.apiKey), gemini.baseUrl, gemini.model,
                    gemini.thinkingLevel, gemini.timeoutMs, fallbackAllowed, aiLogService, aiExecutor);
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
        if (!providerKey("minimax", minimax.apiKey).isBlank()) {
            LlmClient base = createProviderClient("minimax", false, aiLogService, aiExecutor);
            m.put("MINIMAX", languageAware(new ABTestLlmClientWrapper(
                    auroraStageRouter(base, aiLogService, aiExecutor), aiLogService, aiExecutor)));
        }
        if (!providerKey("mimo", mimo.apiKey).isBlank()) {
            LlmClient base = createProviderClient("mimo", false, aiLogService, aiExecutor);
            m.put("MIMO", languageAware(new ABTestLlmClientWrapper(
                    auroraStageRouter(base, aiLogService, aiExecutor), aiLogService, aiExecutor)));
        }
        if (!providerKey("glm", glm.apiKey).isBlank()) {
            LlmClient base = createProviderClient("glm", false, aiLogService, aiExecutor);
            m.put("GLM", languageAware(new ABTestLlmClientWrapper(
                    auroraStageRouter(base, aiLogService, aiExecutor), aiLogService, aiExecutor)));
        }
        if (!providerKey("deepseek", deepseek.apiKey).isBlank()) {
            LlmClient base = createProviderClient("deepseek", false, aiLogService, aiExecutor);
            m.put("DEEPSEEK", languageAware(new ABTestLlmClientWrapper(
                    auroraStageRouter(base, aiLogService, aiExecutor), aiLogService, aiExecutor)));
        }
        if (!providerKey("gemini", gemini.apiKey).isBlank()) {
            LlmClient base = createProviderClient("gemini", false, aiLogService, aiExecutor);
            m.put("GEMINI", languageAware(new ABTestLlmClientWrapper(
                    auroraStageRouter(base, aiLogService, aiExecutor), aiLogService, aiExecutor)));
        }
        m.put("MOCK", languageAware(new MockLlmClient(aiExecutor)));
        return m;
    }

    private LlmClient languageAware(LlmClient client) {
        return new PromptLanguageLlmClient(client, prompt == null ? "auto" : prompt.language);
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
            case "gemini" -> gemini.model;
            default -> model;
        };
    }

    private LlmClient auroraStageRouter(LlmClient fallback, AiLogService aiLogService,
                                        Executor aiExecutor) {
        if (auroraStages == null || !auroraStages.enabled) return fallback;
        // Stage routing spans multiple providers, so provider-specific credentials are
        // mandatory here. The generic llm.api-key may authenticate the selected primary
        // provider, but reusing it for another vendor would create misleading 401/fallbacks.
        String geminiKey = gemini.apiKey == null ? "" : gemini.apiKey.trim();
        String deepseekKey = deepseek.apiKey == null ? "" : deepseek.apiKey.trim();
        LlmClient fast = geminiKey.isBlank() ? null : new GeminiLlmClient(
                geminiKey, gemini.baseUrl, auroraStages.fastModel, "minimal",
                gemini.timeoutMs, false, aiLogService, aiExecutor);
        LlmClient speaker = geminiKey.isBlank() ? null : new GeminiLlmClient(
                geminiKey, gemini.baseUrl, auroraStages.speakerModel,
                auroraStages.speakerThinkingLevel, gemini.timeoutMs,
                false, aiLogService, aiExecutor);
        LlmClient thinker = deepseekKey.isBlank() ? null : new DeepSeekLlmClient(
                deepseekKey, deepseek.baseUrl, auroraStages.thinkerModel,
                deepseek.timeoutMs, false, aiLogService, aiExecutor);
        return new AuroraStageRoutingLlmClient(fallback, fast, speaker, thinker,
                new AuroraStageRoutingLlmClient.StageProfile(
                        false, "minimal", auroraStages.fastTemperature,
                        auroraStages.fastMaxTokens),
                new AuroraStageRoutingLlmClient.StageProfile(
                        true, auroraStages.speakerThinkingLevel,
                        auroraStages.speakerTemperature, auroraStages.speakerMaxTokens),
                new AuroraStageRoutingLlmClient.StageProfile(
                        true, auroraStages.thinkerReasoningEffort,
                        auroraStages.thinkerTemperature, auroraStages.thinkerMaxTokens),
                new AuroraStageRoutingLlmClient.StageProfile(
                        true, auroraStages.thinkerReasoningEffort,
                        auroraStages.criticTemperature, auroraStages.criticMaxTokens));
    }

    private String resolveKey(String key) {
        return (key != null && !key.isBlank()) ? key : (apiKey != null ? apiKey : "");
    }

    /**
     * The generic key belongs only to the selected provider. It must never make every named
     * vendor appear configured or send one vendor's credential to another vendor's endpoint.
     */
    private String providerKey(String providerName, String providerSpecificKey) {
        if (providerSpecificKey != null && !providerSpecificKey.isBlank()) {
            return providerSpecificKey;
        }
        return providerName != null && providerName.equalsIgnoreCase(activeProvider())
                ? (apiKey == null ? "" : apiKey) : "";
    }

    private String configured(String key) {
        return key == null || key.isBlank() ? "not configured" : "configured";
    }
}
