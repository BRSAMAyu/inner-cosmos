package com.innercosmos.scheduler;

import com.innercosmos.ai.capsule.CapsuleSyncService;
import com.innercosmos.entity.CapsuleSyncQueue;
import com.innercosmos.mapper.CapsuleSyncQueueMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * IC-CAP-002 B-2: periodically re-runs FAILED capsule sync rows whose backoff has
 * elapsed and that have not exhausted their attempt budget. Modeled on
 * {@link LetterDeliveryJob}. Rows that hit MAX_ATTEMPTS are left FAILED (no further
 * retries) so the failure stays visible to the user.
 */
@Component
public class CapsuleSyncRetryJob {
    private static final Logger log = LoggerFactory.getLogger(CapsuleSyncRetryJob.class);

    private final CapsuleSyncQueueMapper syncQueueMapper;
    private final CapsuleSyncService syncService;

    public CapsuleSyncRetryJob(CapsuleSyncQueueMapper syncQueueMapper,
                               CapsuleSyncService syncService) {
        this.syncQueueMapper = syncQueueMapper;
        this.syncService = syncService;
    }

    @Scheduled(fixedDelay = 60000)
    @SchedulerLock(name = "capsule-sync-retry", lockAtMostFor = "PT5M", lockAtLeastFor = "PT55S")
    public void retryFailedSyncs() {
        List<CapsuleSyncQueue> retryable =
                syncQueueMapper.findRetryable(LocalDateTime.now(), CapsuleSyncService.MAX_ATTEMPTS);
        if (retryable.isEmpty()) return;
        int retried = 0;
        for (CapsuleSyncQueue row : retryable) {
            try {
                syncService.retryFailed(row.id);
                retried++;
            } catch (Exception e) {
                log.error("Retry sweep failed for queue {}: {}", row.id, e.getMessage());
            }
        }
        log.info("Capsule sync retry sweep processed {} of {} retryable rows", retried, retryable.size());
    }
}
