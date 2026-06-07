package com.innercosmos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ChatRequest {
    @NotNull(message = "sessionId is required")
    public Long sessionId;
    @NotBlank(message = "message is required")
    public String message;
    public String inputType = "TEXT";
    public Integer audioDurationSec;
    public Double speechRate;
    public Integer pauseCount;
    public Integer longPauseCount;
    public String emotionHint;
    public String mode;
    public String clientMessageId;
    public String timezone;
    public String localTimeLabel;
    public String weatherType;
    public String weatherDescription;
    public Double temperature;
    public String locationLabel;
    public Double latitude;
    public Double longitude;
    public String aiProviderPreference;
}
