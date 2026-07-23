package com.innercosmos.service;

/**
 * Gemini audit 1.6 (CONFIRMED/P1): recomputes emotional-gravity decay for a user's active memory
 * cards. This used to be an independent {@code @Async} event listener racing (no ordering
 * guarantee) against {@code MemoryExtractListener} on the same {@code DialogFinishedEvent} -- a
 * dialog's newly-extracted/updated memory card could be missed by this recompute if it ran first.
 * It is now an unconditionally-available service so both the legacy in-process path
 * (MemoryExtractListener, calling it synchronously AFTER extraction, in the same async task) and
 * the durable-outbox path (DialogFinishedProjectionHandler, in the same transactional projection)
 * can call it in the correct order -- extraction always completes before this reads the card list.
 */
public interface GravityRecalculationService {
    void recalculateForUser(Long userId);
}
