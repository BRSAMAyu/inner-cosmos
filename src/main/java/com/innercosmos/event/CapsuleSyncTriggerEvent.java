package com.innercosmos.event;

/**
 * IC-CAP-002 B-1: published whenever a user's portrait / memory / nightly baseline
 * bridge changes, signalling that their capsules may need a (PENDING) sync proposal.
 *
 * Published from three sites:
 *  - UserPortraitService.applyDeltas (portrait write; also covers the nightly
 *    EmotionBaselineService.bridgeToPortrait path, which routes through applyDeltas)
 *  - MemoryServiceImpl.extractFromSession (memory change)
 *
 * Consumed by {@link CapsuleRegenerateListener} (@Async), which delegates to
 * CapsuleSyncService.onPortraitOrRelationshipChanged(userId). The terminal effect
 * is deduped (one PENDING row per capsule) so multiple sources cannot storm the queue.
 */
public class CapsuleSyncTriggerEvent {
    public final Long userId;

    public CapsuleSyncTriggerEvent(Long userId) {
        this.userId = userId;
    }
}
