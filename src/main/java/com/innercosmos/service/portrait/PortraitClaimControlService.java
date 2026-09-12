package com.innercosmos.service.portrait;

import com.innercosmos.entity.UnderstandingClaim;

/**
 * CP-23 owner actions on portrait claims: parking (搁置) an inference the user does not
 * recognize, restoring it, and deleting a claim outright. All three are owner-scoped and
 * audit-recorded; a suppressed or deleted claim disappears from the correctable-portrait
 * view AND from Aurora's per-turn context (both read status=ACTIVE only), so the change
 * is fully adopted from the next turn on.
 */
public interface PortraitClaimControlService {

    /** Park a claim: hidden from view and Aurora context, row kept for audit. */
    UnderstandingClaim suppress(Long userId, Long claimId, String reason);

    /** Un-park a previously suppressed claim. */
    UnderstandingClaim restore(Long userId, Long claimId);

    /** Remove a claim (soft delete): gone from every current surface, audit kept. */
    UnderstandingClaim delete(Long userId, Long claimId, String reason);
}
