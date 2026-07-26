package com.innercosmos.dto;

import jakarta.validation.constraints.NotBlank;

public class LiveChatInviteResponseRequest {
    @NotBlank
    public String decision;
}
