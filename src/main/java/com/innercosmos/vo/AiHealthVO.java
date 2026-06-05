package com.innercosmos.vo;

public class AiHealthVO {
    public String mode;
    public String provider;
    public String model;
    public Boolean apiKeyConfigured;
    public Boolean fallbackAllowed;
    public Boolean mockProvider;
    public String asrProvider;
    public String asrModel;
    public Boolean asrKeyConfigured;
    public Boolean asrMockProvider;
    public Boolean lastSuccess;
    public Boolean lastFallbackUsed;
    public String lastModule;
    public String lastProvider;
    public String lastModel;
    public String lastError;
    public Long lastLatencyMs;
}
