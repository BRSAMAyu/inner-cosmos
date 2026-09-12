package com.innercosmos.service.portrait;

import java.util.List;

/**
 * CP-23 correctable portrait view: every dimension the system believes about the user,
 * with an honest state (confirmed / inferred / conflicting / superseded) — and UNKNOWN
 * (shown as absent, never filled from a fixed personality template). No aggregate
 * personality score exists by design.
 */
public interface PortraitClaimViewService {

    record ClaimView(
            Long claimId,
            String claimKey,
            String claimType,
            String state,
            String authorityLevel,
            String value,
            String version,
            String scope,
            String sourceType) {
    }

    /**
     * @param suppressed the owner's parked claims (status=SUPPRESSED): out of every current
     *                   surface, listed only here so the owner can restore them.
     */
    record PortraitView(List<ClaimView> claims, int unknownDimensions, String explanation,
                        List<ClaimView> suppressed) {
    }

    /**
     * Derive the view from ACTIVE understanding claims:
     * CONFIRMED — user-confirmed/corrected authority; INFERRED — model inference;
     * CONFLICTING — two different ACTIVE values for one claim key (recent wins, older flagged);
     * SUPERSEDED rows never appear. Keys with no material simply do not appear — the UI
     * shows UNKNOWN for the well-known dimensions (counted here).
     */
    PortraitView view(Long userId);
}
