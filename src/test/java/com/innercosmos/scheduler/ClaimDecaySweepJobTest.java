package com.innercosmos.scheduler;

import com.innercosmos.service.ClaimCandidateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Track A / A2 — the scheduled half of the claim decay sweep just has to call through to the real
 * batch method with the configured batch size; the decay math itself is pinned by
 * {@code ClaimConfidenceDecayPolicyTest} and the service-level integration test.
 */
@ExtendWith(MockitoExtension.class)
class ClaimDecaySweepJobTest {

    @Mock private ClaimCandidateService claimCandidateService;

    @Test
    void runDelegatesToTheConfiguredBatchSize() {
        when(claimCandidateService.sweepStaleCandidates(50)).thenReturn(3);

        ClaimDecaySweepJob job = new ClaimDecaySweepJob(claimCandidateService, 50);
        job.run();

        verify(claimCandidateService).sweepStaleCandidates(50);
    }

    @Test
    void runToleratesZeroDismissedWithoutError() {
        when(claimCandidateService.sweepStaleCandidates(200)).thenReturn(0);

        ClaimDecaySweepJob job = new ClaimDecaySweepJob(claimCandidateService, 200);
        job.run();

        verify(claimCandidateService).sweepStaleCandidates(200);
    }
}
