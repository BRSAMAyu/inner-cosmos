package com.innercosmos.scheduler;

import com.innercosmos.service.ClaimCandidateService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Track A / A2 — background half of {@link ClaimCandidateService#sweepStaleCandidates}. A candidate
 * the owner never revisits should still decay and eventually stop being treated as live evidence;
 * without this job, decay would only ever be applied lazily inside {@code listCandidates}, so a
 * candidate nobody opens the review list for would sit forever at its original (stale) confidence.
 * Global/batch, not per-user — mirrors {@link MemoryEmbeddingRebuildJob}'s simpler batch pattern
 * rather than {@code NightlyMemorySettlementJob}'s per-user loop, since decay needs no per-user
 * portrait/gravity context, only the claim rows themselves.
 */
@Component
@ConditionalOnExpression("'${inner-cosmos.runtime.role:all}' == 'all' or '${inner-cosmos.runtime.role:all}' == 'scheduler'")
public class ClaimDecaySweepJob {
    private static final Logger log = LoggerFactory.getLogger(ClaimDecaySweepJob.class);
    private final ClaimCandidateService claimCandidateService;
    private final int batchSize;

    public ClaimDecaySweepJob(ClaimCandidateService claimCandidateService,
                              @Value("${claim.decay.sweep-batch-size:200}") int batchSize) {
        this.claimCandidateService = claimCandidateService;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "0 30 2 * * ?")
    @SchedulerLock(name = "claim-decay-sweep", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1S")
    public void run() {
        int dismissed = claimCandidateService.sweepStaleCandidates(batchSize);
        if (dismissed > 0) {
            log.info("Claim decay sweep dismissed {} stale unconfirmed candidates", dismissed);
        }
    }
}
