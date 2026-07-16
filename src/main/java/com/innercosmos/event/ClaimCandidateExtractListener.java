package com.innercosmos.event;

import com.innercosmos.service.ClaimCandidateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Campaign B — automatically stage user-model claim candidates when a conversation finishes. Runs
 * after commit and off the request thread so it never blocks or fails the dialog; a staging failure
 * is logged, not propagated. Mirrors {@link MemoryExtractListener} but populates claim candidates
 * (which, unlike memory extraction, retain per-message provenance).
 */
@Component
public class ClaimCandidateExtractListener {
    private static final Logger log = LoggerFactory.getLogger(ClaimCandidateExtractListener.class);
    private final ClaimCandidateService claimCandidateService;

    public ClaimCandidateExtractListener(ClaimCandidateService claimCandidateService) {
        this.claimCandidateService = claimCandidateService;
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDialogFinished(DialogFinishedEvent event) {
        try {
            int staged = claimCandidateService.stageForSession(event.userId, event.sessionId);
            if (staged > 0) {
                log.debug("Staged {} claim candidates for session {}", staged, event.sessionId);
            }
        } catch (Exception e) {
            log.error("Claim candidate extraction failed for session {}: {}",
                    event.sessionId, e.getMessage(), e);
        }
    }
}
