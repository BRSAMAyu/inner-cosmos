package com.innercosmos.dto;

import jakarta.validation.constraints.NotNull;

public class LiveChatInviteRequest {
    @NotNull
    public Long targetUserId;

    @NotNull
    public Integer durationMinutes;
}
