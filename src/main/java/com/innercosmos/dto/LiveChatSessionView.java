package com.innercosmos.dto;

import java.time.OffsetDateTime;

public record LiveChatSessionView(
        Long id,
        Long inviteId,
        Long participantOneId,
        String participantOneNickname,
        Long participantTwoId,
        String participantTwoNickname,
        Integer durationMinutes,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime endsAt,
        OffsetDateTime endedAt,
        Long endedByUserId) {
}
