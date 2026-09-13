package com.innercosmos.event.reliable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CP-40 admin dead-letter dashboard view over an existing DEAD {@code tb_outbox_event} row.
 * Presentation tuple only — the row itself is never mutated by the listing. {@code payloadSummary}
 * is a truncated preview (payloads are sensitive-free ID/metadata bodies per the writers); it is
 * meant to help an operator recognise the event, not to re-deliver it.
 */
public record OutboxDeadLetter(
        long id,
        UUID eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        String payloadSummary,
        int attempts,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime lastAttemptAt) {
}
