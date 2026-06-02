package com.innercosmos.ai.client;

import java.util.ArrayList;
import java.util.List;

public class LlmRequest {
    public Long userId;
    public String moduleName;
    public String prompt;
    public String requestJson;
    public List<String> recentMessages = new ArrayList<>();
    public Boolean forceMock; // Force mock mode for A/B testing

    public LlmRequest(Long userId, String moduleName, String prompt) {
        this.userId = userId;
        this.moduleName = moduleName;
        this.prompt = prompt;
    }
}
