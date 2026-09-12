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
            String claimKey,
            String claimType,
            String state,
            String authorityLevel,
            String value,
            String version,
            String scope,
            String sourceType) {
    }

    record PortraitView(List<ClaimView> claims, int unknownDimensions, String explanation) {
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
