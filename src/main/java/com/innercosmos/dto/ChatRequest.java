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
    public String locale;
    public String region;
    public String timezone;
    public String localTimeLabel;
    public String weatherType;
    public String weatherDescription;
    public Double temperature;
    public String locationLabel;
    public Double latitude;
    public Double longitude;
    public String aiProviderPreference;
    /** Internal streaming hint: a short foreground acknowledgement is already being shown. */
    public boolean foregroundAcknowledgementSent;
    /** Exact server-generated foreground text already shown by the client; persisted before deep bubbles. */
    public String foregroundAcknowledgementText;
    /** Observability label returned by the fast expression-core endpoint. */
    public String foregroundAcknowledgementSource;
}
