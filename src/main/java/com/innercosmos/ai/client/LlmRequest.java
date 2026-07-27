package com.innercosmos.ai.client;

import java.util.ArrayList;
import java.util.List;

public class LlmRequest {
    public Long userId;
    public String moduleName;
    /**
     * Provider-level system instruction. When present, clients must send it with
     * role=system instead of demoting safety and identity boundaries into user text.
     */
    public String systemPrompt;
    public String prompt;
    public String requestJson;
    public String preferredProvider;
    public List<String> recentMessages = new ArrayList<>();
    public Boolean forceMock; // Force mock mode for A/B testing
    /**
     * M-012: per-mode sampling temperature. NULLABLE by design — null means "no
     * override", and every provider client falls back to its own existing hardcoded
     * default, so non-Aurora calls remain byte-identical. Only the Aurora reply/greeting
     * path sets this from the active mode's {@code ModeStrategy.temperature()}.
     */
    public Double temperature;
    /**
     * Per-call reasoning budget. Foreground response modules set this to {@code false};
     * background planner/critic modules may set it to {@code true}. Provider clients that
     * support an explicit thinking toggle must honor it.
     */
    public Boolean thinkingEnabled;
    /**
     * Optional provider reasoning-effort hint for background work. It is deliberately separate
     * from {@link #thinkingEnabled}: callers may enable thinking without claiming a measured
     * high-effort budget. Provider clients must omit this field when it is blank.
     */
    public String reasoningEffort;
    /**
     * Optional output-token budget for this module. Null preserves the provider-wide generous
     * reply budget, while structured multi-kernel stages can reserve only the room their schema
     * needs. This is separate from {@link #thinkingEnabled}: the background planner may still
     * reason deeply, but it should not inherit the same 4096-token envelope as a long-form reply.
     */
    public Integer maxTokens;
    /**
     * Optional provider-call deadline for this module. Null keeps the provider default.
     * Aurora's progressive runtime uses a shorter bounded deadline for each background stage so
     * one slow provider attempt cannot silently double the user's wait.
     */
    public Integer timeoutMs;
    /**
     * Optional wall-clock budget for the complete real-provider chain. Each provider still gets
     * its own attempt timeout, but failover must stop once this shared deadline is exhausted.
     */
    public Integer totalTimeoutMs;
    /**
     * Optional same-request provider retry switch. Null preserves legacy retry-once behavior.
     * Structured Aurora stages set false because their next stage already has a deterministic
     * business fallback and retrying a 30-second reasoning request harms the live experience.
     */
    public Boolean retryEnabled;

    public LlmRequest(Long userId, String moduleName, String prompt) {
        this.userId = userId;
        this.moduleName = moduleName;
        this.prompt = prompt;
    }

    public String systemPromptOr(String fallback) {
        return systemPrompt == null || systemPrompt.isBlank() ? fallback : systemPrompt;
    }

    public int timeoutMsOr(int fallback) {
        return timeoutMs == null || timeoutMs <= 0 ? fallback : timeoutMs;
    }

    public int maxTokensOr(int fallback) {
        return maxTokens == null || maxTokens <= 0 ? fallback : maxTokens;
    }

    public int totalTimeoutMsOr(int fallback) {
        return totalTimeoutMs == null || totalTimeoutMs <= 0 ? fallback : totalTimeoutMs;
    }

    public boolean retryEnabledOrDefault() {
        return retryEnabled == null || retryEnabled;
    }
}
