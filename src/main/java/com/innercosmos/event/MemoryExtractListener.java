package com.innercosmos.event;

import com.innercosmos.service.GravityRecalculationService;
import com.innercosmos.service.MemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Gemini audit 1.6 (CONFIRMED/P1): this used to be one of TWO independent {@code @Async}
 * listeners on {@link DialogFinishedEvent} -- this one extracting/creating the session's memory
 * card, and a separate {@code GravityRecalculateListener} independently recomputing gravity for
 * the user's whole active card set -- with no ordering guarantee between them. Spring does not
 * guarantee async listener completion order, so the gravity recompute could run BEFORE this
 * extraction committed, missing the just-created/updated card for that recompute pass.
 *
 * <p>Per the audit's explicit fix contract, adding {@code @Order} would NOT have fixed this (it
 * does not order asynchronous COMPLETION, only synchronous dispatch). The two steps are now a
 * single ordered sequence inside this one listener: extraction, then the gravity recompute --
 * both run in the same async task, so the recompute always sees the just-committed card.
 */
@Component
@ConditionalOnProperty(name = "inner-cosmos.events.outbox.enabled", havingValue = "false", matchIfMissing = true)
public class MemoryExtractListener {
    private static final Logger log = LoggerFactory.getLogger(MemoryExtractListener.class);
    private final MemoryService memoryService;
    private final GravityRecalculationService gravityRecalculationService;

    public MemoryExtractListener(MemoryService memoryService, GravityRecalculationService gravityRecalculationService) {
        this.memoryService = memoryService;
        this.gravityRecalculationService = gravityRecalculationService;
    }

    @Async("taskExecutor")
    @org.springframework.transaction.event.TransactionalEventListener(phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDialogFinished(DialogFinishedEvent event) {
        try {
            memoryService.extractFromSession(event.userId, event.sessionId);
        } catch (Exception e) {
            log.error("Memory extraction failed for session {}: {}", event.sessionId, e.getMessage(), e);
            // Deliberately fall through to the gravity recompute below rather than returning --
            // it recomputes decay for the user's OTHER existing cards too, which do not depend on
            // this extraction and must not miss their refresh just because this one step failed
            // (matches the two-listener original's failure isolation; only the ORDERING changed).
        }
        // Gemini audit 1.6: gravity recompute runs AFTER extraction was attempted, in the same
        // sequential task -- never as a second, independently-scheduled async listener racing
        // against this one with no ordering guarantee. Own try/catch so this @Async,
        // fallbackExecution=true, after-commit listener never lets an uncaught exception escape
        // regardless of what its injected collaborator does.
        try {
            gravityRecalculationService.recalculateForUser(event.userId);
        } catch (Exception e) {
            log.error("Gravity recalculation failed for user {}: {}", event.userId, e.getMessage(), e);
        }
    }
}
