package com.innercosmos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LiveChatMessageRequest {
    @NotBlank
    @Size(max = 2000)
    public String messageBody;
}
