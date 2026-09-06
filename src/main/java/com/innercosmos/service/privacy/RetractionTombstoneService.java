package com.innercosmos.service.privacy;

import java.util.Set;

/**
 * CP-15 anti-resurrection layer: tombstones block retracted subjects at READ time and the
 * watermark proves, after a backup restore, how far rights requests had progressed. Business
 * recovery must catch up to the durable watermark before reopening access.
 */
public interface RetractionTombstoneService {

    /** Immutable marker write; idempotent per (subjectType, subjectId). Returns the watermark id. */
    long record(String subjectType, Long subjectId, Long ownerUserId, String consentVersion, String reason);

    /** Whether a subject is blocked — checked at actual use time, not just task-enqueue time. */
    boolean isBlocked(String subjectType, Long subjectId);

    /** Blocked subject ids of one type, for read-path filtering. */
    Set<Long> blockedIds(String subjectType, Long ownerUserId);

    /** Current rights watermark (max tombstone id); 0 when nothing was ever retracted. */
    long watermark();

    /**
     * Disaster-recovery catch-up: given the watermark proven from the independent ledger,
     * re-apply all tombstones above it onto the live business state (rows a backup restore
     * brought back are re-blocked). Returns how many tombstones were replayed.
     */
    int catchUpFromWatermark(long provenWatermark);
}
