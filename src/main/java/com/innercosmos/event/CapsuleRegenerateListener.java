package com.innercosmos.event;

import com.innercosmos.ai.capsule.CapsuleSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * IC-CAP-002 B-1: consumes {@link CapsuleSyncTriggerEvent} and drives the capsule
 * sync. Runs @Async so portrait/memory write paths are not blocked; the PENDING-row
 * dedup in CapsuleSyncService keeps the queue storm-safe even under multiple sources.
 *
 * Decoupling via the event bus (rather than direct injection) is required: CapsuleSyncService
 * already injects UserPortraitService, so injecting CapsuleSyncService back into the portrait
 * write path would create a circular dependency. The event bus breaks that cycle.
 *
 * IC-CAP-002 FIX-2 (event ordering): the publish sites (UserPortraitService.applyDeltas,
 * MemoryServiceImpl.extractFromSession) are @Transactional. With a plain @EventListener the
 * @Async handler could start BEFORE the publisher's transaction commits and then read
 * half-written / uncommitted rows. Binding to AFTER_COMMIT guarantees the sync only runs
 * once the triggering write is durably committed. @Async is retained so the (now post-commit)
 * sync still runs off the request thread.
 */
@Component
public class CapsuleRegenerateListener {
    private static final Logger log = LoggerFactory.getLogger(CapsuleRegenerateListener.class);

    private final CapsuleSyncService syncService;

    public CapsuleRegenerateListener(CapsuleSyncService syncService) {
        this.syncService = syncService;
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSyncTrigger(CapsuleSyncTriggerEvent event) {
        if (event == null || event.userId == null) return;
        try {
            syncService.onPortraitOrRelationshipChanged(event.userId);
        } catch (Exception e) {
            log.error("Capsule sync trigger failed for user {}: {}", event.userId, e.getMessage());
        }
    }
}
