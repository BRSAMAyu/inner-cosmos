package com.innercosmos.ai.client;

import java.util.ArrayList;
import java.util.List;

public class LlmRequest {
    public Long userId;
    public String moduleName;
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

    public LlmRequest(Long userId, String moduleName, String prompt) {
        this.userId = userId;
        this.moduleName = moduleName;
        this.prompt = prompt;
    }
}
