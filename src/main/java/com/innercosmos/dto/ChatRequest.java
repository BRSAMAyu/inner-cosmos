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
}
