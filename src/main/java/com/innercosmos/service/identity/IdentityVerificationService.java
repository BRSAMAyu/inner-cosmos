package com.innercosmos.service.identity;

import com.innercosmos.entity.IdentityVerification;
import java.util.List;

/**
 * CP-13 VERIFIED_ID upgrade chain: issue a challenge through the configured provider,
 * consume its single-shot outcome once, then either upgrade the account
 * (SELF_DECLARED -> VERIFIED_ID with the provider-confirmed birth date) or — when the
 * verified birth date is under 18 — link straight into the CP-08 minor-intercept state.
 */
public interface IdentityVerificationService {

    record ChallengeView(String providerReference, String instructions, String expiresAt) {
    }

    record StatusView(String ageGateMethod, String birthDate, String latestStatus,
                      String latestFailureReason, List<HistoryRow> history) {
    }

    record HistoryRow(String method, String provider, String status, String verifiedBirthDate,
                      String createdAt) {
    }

    /** Start a new verification challenge (previous pending ones are expired). */
    ChallengeView initiate(Long userId);

    /**
     * Consume the provider outcome for one reference. Enforces: same user (no crossed
     * accounts), PENDING status, not expired, not previously consumed (replay-proof).
     */
    StatusView confirm(Long userId, String providerReference, String answer);

    StatusView statusOf(Long userId);
}
