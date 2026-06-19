package com.innercosmos.scheduler;

import com.innercosmos.ai.capsule.CapsuleSyncService;
import com.innercosmos.entity.CapsuleSyncQueue;
import com.innercosmos.mapper.CapsuleSyncQueueMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * IC-CAP-002 B-2: the retry job re-runs FAILED rows whose backoff has elapsed and
 * that have not exhausted their attempt budget; once attemptCount reaches MAX_ATTEMPTS
 * the row stops being retried (left FAILED, failure visible).
 */
@ExtendWith(MockitoExtension.class)
class CapsuleSyncRetryJobTest {

    @Mock private CapsuleSyncQueueMapper syncQueueMapper;
    @Mock private CapsuleSyncService syncService;

    private CapsuleSyncQueue failed(Long id, int attempts) {
        CapsuleSyncQueue q = new CapsuleSyncQueue();
        q.id = id;
        q.userId = 1L;
        q.capsuleId = 10L;
        q.status = "FAILED";
        q.attemptCount = attempts;
        q.nextRetryAt = LocalDateTime.now().minusMinutes(1); // backoff elapsed
        return q;
    }

    @Test
    @DisplayName("B-2: the job retries each retryable FAILED row exactly once per tick")
    void retryJob_retriesRetryableRows() {
        CapsuleSyncQueue a = failed(1L, 1);
        CapsuleSyncQueue b = failed(2L, 2);
        when(syncQueueMapper.findRetryable(any(LocalDateTime.class), eq(CapsuleSyncService.MAX_ATTEMPTS)))
                .thenReturn(List.of(a, b));

        CapsuleSyncRetryJob job = new CapsuleSyncRetryJob(syncQueueMapper, syncService);
        job.retryFailedSyncs();

        verify(syncService).retryFailed(1L);
        verify(syncService).retryFailed(2L);
    }

    @Test
    @DisplayName("B-2: a row at MAX_ATTEMPTS is not returned by findRetryable → never retried (gives up)")
    void retryJob_givesUpAtMaxAttempts() {
        // findRetryable's SQL filters attempt_count < MAX_ATTEMPTS, so an exhausted row
        // is simply not returned. Simulate the job seeing an empty retryable set.
        when(syncQueueMapper.findRetryable(any(LocalDateTime.class), eq(CapsuleSyncService.MAX_ATTEMPTS)))
                .thenReturn(List.of());

        CapsuleSyncRetryJob job = new CapsuleSyncRetryJob(syncQueueMapper, syncService);
        job.retryFailedSyncs();

        verify(syncService, never()).retryFailed(any());
    }

    @Test
    @DisplayName("B-2: simulate 3 ticks where each retry fails and increments — stops once budget hits MAX")
    void retryJob_retriesUntilMaxThenGivesUp() {
        // Stateful: the row's attemptCount climbs each retry; findRetryable only returns
        // it while attemptCount < MAX_ATTEMPTS, mirroring the production SQL filter.
        CapsuleSyncQueue row = failed(5L, 0);
        when(syncQueueMapper.findRetryable(any(LocalDateTime.class), eq(CapsuleSyncService.MAX_ATTEMPTS)))
                .thenAnswer(inv -> row.attemptCount < CapsuleSyncService.MAX_ATTEMPTS
                        ? List.of(row) : List.<CapsuleSyncQueue>of());
        // Each retry bumps the attempt counter (as the real regenerateOne would on failure).
        doAnswer(inv -> { row.attemptCount++; return null; })
                .when(syncService).retryFailed(5L);

        CapsuleSyncRetryJob job = new CapsuleSyncRetryJob(syncQueueMapper, syncService);
        // Run many ticks; it must self-limit to MAX_ATTEMPTS retries total.
        for (int i = 0; i < 6; i++) job.retryFailedSyncs();

        verify(syncService, times(CapsuleSyncService.MAX_ATTEMPTS)).retryFailed(5L);
        assertEquals(CapsuleSyncService.MAX_ATTEMPTS, row.attemptCount);
    }
}
