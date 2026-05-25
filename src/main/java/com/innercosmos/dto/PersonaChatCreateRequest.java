package com.innercosmos.dto;

import jakarta.validation.constraints.NotNull;

public class PersonaChatCreateRequest {
    @NotNull(message = "capsuleId is required")
    public Long capsuleId;
}
