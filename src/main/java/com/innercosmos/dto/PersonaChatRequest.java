package com.innercosmos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class PersonaChatRequest {
    @NotNull(message = "sessionId is required")
    public Long sessionId;
    @NotBlank(message = "message is required")
    @Size(max = 2000, message = "消息内容不能超过2000字")
    public String message;
}
