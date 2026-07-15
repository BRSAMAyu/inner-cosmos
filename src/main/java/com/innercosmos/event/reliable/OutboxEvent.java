package com.innercosmos.event.reliable;

import java.time.LocalDateTime;
import java.util.UUID;

public record OutboxEvent(
        long id,
        UUID eventId,
        String dedupKey,
        String aggregateType,
        String aggregateId,
        String eventType,
        int schemaVersion,
        String payload,
        String traceId,
        int attempts,
        String lockedBy,
        LocalDateTime lockedUntil) {
}
