package com.innercosmos.dto;

import java.time.OffsetDateTime;

public record LiveChatMessageView(
        Long id,
        Long sessionId,
        Long senderUserId,
        String senderNickname,
        String messageBody,
        OffsetDateTime createdAt) {
}
