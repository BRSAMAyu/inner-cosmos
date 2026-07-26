package com.innercosmos.dto;

import java.time.OffsetDateTime;

public record LiveChatInviteView(
        Long id,
        Long inviterUserId,
        String inviterNickname,
        Long inviteeUserId,
        String inviteeNickname,
        Integer durationMinutes,
        String status,
        OffsetDateTime expiresAt,
        OffsetDateTime respondedAt,
        OffsetDateTime createdAt) {
}
