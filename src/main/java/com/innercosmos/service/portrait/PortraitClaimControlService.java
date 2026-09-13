package com.innercosmos.service.portrait;

import com.innercosmos.entity.UnderstandingClaim;

/**
 * CP-23 owner actions on portrait claims: parking (搁置) an inference the user does not
 * recognize, restoring it, and deleting a claim outright. All three are owner-scoped and
 * audit-recorded; a suppressed or deleted claim disappears from the correctable-portrait
 * view AND from Aurora's per-turn context (both read status=ACTIVE only), so the change
 * is fully adopted from the next turn on.
 *
 * <p>CP-21 optimistic concurrency: every action is a transition on an existing claim row and
 * accepts {@code expectedVersion} — the {@code version} value the caller last saw (exposed by
 * {@link PortraitClaimViewService.ClaimView#version()}). A stale expectation is rejected as
 * {@code ErrorCode.CONFLICT} (409) instead of last-writer-wins; a successful transition bumps
 * the row's version by one. {@code null} keeps the legacy no-expectation behavior (still an
 * atomic conditional update, so a racing transition surfaces as CONFLICT rather than a lost
 * update) — the web client passes the version it rendered, per CP-21.</p>
 */
public interface PortraitClaimControlService {

    /** Park a claim: hidden from view and Aurora context, row kept for audit. */
    UnderstandingClaim suppress(Long userId, Long claimId, String reason, Integer expectedVersion);

    /** Un-park a previously suppressed claim. */
    UnderstandingClaim restore(Long userId, Long claimId, Integer expectedVersion);

    /** Remove a claim (soft delete): gone from every current surface, audit kept. */
    UnderstandingClaim delete(Long userId, Long claimId, String reason, Integer expectedVersion);
}
