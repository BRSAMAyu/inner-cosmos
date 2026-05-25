package com.innercosmos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class PersonaChatRequest {
    @NotNull(message = "sessionId is required")
    public Long sessionId;
    @NotBlank(message = "message is required")
    public String message;
}
