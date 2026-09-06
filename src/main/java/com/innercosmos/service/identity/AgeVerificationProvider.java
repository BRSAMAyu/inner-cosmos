package com.innercosmos.service.identity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * CP-13 age/identity verification channel. Real channels (operator SMS with recycled-number
 * handling, Alipay-certified, manual review) land with provider contracts; until then the
 * product fails CLOSED — it never silently degrades to a weaker check (blueprint recovery
 * clause: identity provider failure must not weaken verification).
 */
public interface AgeVerificationProvider {

    String name();

    /** Issue a challenge for this user; the reference is opaque and single-purpose. */
    AgeChallenge issueChallenge(Long userId);

    /**
     * Verify the user's answer for a previously issued reference. Implementations must be
     * idempotent per reference and must never return a birth date with verified=false.
     */
    AgeOutcome verify(Long userId, String providerReference, String answer);

    record AgeChallenge(String providerReference, String instructions, LocalDateTime expiresAt) {
    }

    record AgeOutcome(boolean verified, LocalDate birthDate, String failureReason) {
    }
}
