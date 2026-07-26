package com.innercosmos.event;

import com.innercosmos.service.ClaimCandidateService;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Refreshes candidates after each complete turn. A per-session lease and monotonic revision guard
 * prevent concurrent turns from allowing an older extraction to overwrite a newer snapshot.
 */
@Component
public class ClaimCandidateTurnExtractListener {
    private static final Logger log = LoggerFactory.getLogger(ClaimCandidateTurnExtractListener.class);
    private final ClaimCandidateService claimCandidateService;
    private final ConcurrentHashMap<Long, Object> leases = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> latestRevision = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> processedRevision = new ConcurrentHashMap<>();

    public ClaimCandidateTurnExtractListener(ClaimCandidateService claimCandidateService) {
        this.claimCandidateService = claimCandidateService;
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTurnPersisted(DialogTurnPersistedEvent event) {
        if (event == null || event.sessionId() == null) return;
        long revision = event.revision() == null ? 0L : event.revision();
        latestRevision.merge(event.sessionId(), revision, Math::max);
        Object lease = leases.computeIfAbsent(event.sessionId(), ignored -> new Object());
        synchronized (lease) {
            if (revision < latestRevision.getOrDefault(event.sessionId(), revision)) return;
            if (revision <= processedRevision.getOrDefault(event.sessionId(), -1L)) return;
            try {
                claimCandidateService.stageForSession(event.userId(), event.sessionId());
                processedRevision.put(event.sessionId(), revision);
            } catch (Exception failure) {
                log.error("Per-turn claim extraction failed for session {}: {}",
                        event.sessionId(), failure.getMessage(), failure);
            }
        }
    }
}
