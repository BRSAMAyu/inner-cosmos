package com.innercosmos.ai.claim;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Track A / A2 — time-aware confidence for {@code UnderstandingClaim} rows.
 *
 * <p>TRACK-A-LIVING-INTELLIGENCE.md §6 requires the claim graph to "decay weak inferences and never
 * decay explicit user assertions as if they were guesses." Before this policy existed, {@code
 * tb_understanding_claim.confidence} was written once at insert time (by {@code
 * ClaimCandidateServiceImpl} or {@code UserCorrectionServiceImpl}) and never touched again — a
 * six-month-old, never-confirmed "maybe" from a single ambiguous sentence carried exactly the same
 * weight as one the user restated ten times last week, and an explicit user correction had no special
 * protection from decay logic because none existed at all.
 *
 * <p>This class is a pure function of (base confidence, evidence tier, elapsed time). It never
 * mutates stored state itself — callers decide what to do with the result (display a decayed value,
 * or dismiss a candidate whose belief has decayed below the floor). Keeping decay as a read-time
 * computation over an immutable {@code confidence} column — rather than an in-place nightly
 * multiplication — avoids compounding/double-decay bugs and keeps the original evidence-based
 * confidence available as provenance.
 *
 * <p>Evidence tiers below {@code USER_CORRECTION}/{@code USER_CONFIRMED} (the two the extractor can
 * never produce directly, see {@link ClaimAuthority}) decay at different half-lives reflecting how
 * much standalone weight that tier deserves as time passes without reinforcement:
 * <ul>
 *   <li>{@code REPEATED_EXPLICIT} (repeated, explicit user statements) — slowest, 90 days.</li>
 *   <li>{@code REPEATED_BEHAVIOR} (a recurring pattern observed, not merely asserted) — 60 days.</li>
 *   <li>{@code SINGLE_EXPLICIT} (one clear self-predication) — 30 days.</li>
 *   <li>{@code MODEL_INFERENCE} (a pure model guess) — fastest, 14 days.</li>
 * </ul>
 * Explicit user assertions ({@code USER_CORRECTION}, the confirm-path authority level every
 * ACTIVE/confirmed claim carries — see {@code UserCorrectionServiceImpl#confirm} and {@code
 * ClaimCandidateServiceImpl#confirmCandidate}, plus the reserved {@code USER_CONFIRMED} tier) never
 * decay: {@link #neverDecays(String)} returns true and {@link #effectiveConfidence} returns the base
 * value unchanged regardless of elapsed time.
 */
public final class ClaimConfidenceDecayPolicy {

    /** Below this effective confidence, a still-unconfirmed candidate is considered stale enough to auto-dismiss. */
    public static final double DISMISS_THRESHOLD = 0.15;

    /** Fallback half-life (days) for an unrecognized/absent authority level — treated as a weak, unverified guess. */
    private static final double DEFAULT_HALF_LIFE_DAYS = 21.0;

    private ClaimConfidenceDecayPolicy() {
    }

    /** True for the two explicit-user-act tiers that must never be treated as decaying guesses. */
    public static boolean neverDecays(String authorityLevel) {
        return "USER_CORRECTION".equals(authorityLevel) || "USER_CONFIRMED".equals(authorityLevel);
    }

    /** Half-life, in days, for the given evidence tier. Only meaningful when {@link #neverDecays} is false. */
    public static double halfLifeDays(String authorityLevel) {
        if (authorityLevel == null) return DEFAULT_HALF_LIFE_DAYS;
        return switch (authorityLevel) {
            case ClaimAuthority.REPEATED_EXPLICIT -> 90.0;
            case ClaimAuthority.REPEATED_BEHAVIOR -> 60.0;
            case ClaimAuthority.SINGLE_EXPLICIT -> 30.0;
            case ClaimAuthority.MODEL_INFERENCE -> 14.0;
            default -> DEFAULT_HALF_LIFE_DAYS;
        };
    }

    /**
     * Exponential half-life decay from {@code referenceTime} (last time this claim's evidence was
     * created or reinforced — i.e. {@code updatedAt}, falling back to {@code createdAt}) to {@code
     * now}. Explicit-assertion tiers return {@code baseConfidence} unmodified. Never returns a
     * negative value; the floor is 0.
     */
    public static double effectiveConfidence(double baseConfidence, String authorityLevel,
                                              LocalDateTime referenceTime, LocalDateTime now) {
        if (neverDecays(authorityLevel)) return baseConfidence;
        if (referenceTime == null || now == null || !now.isAfter(referenceTime)) return Math.max(0, baseConfidence);
        double elapsedDays = Duration.between(referenceTime, now).toMinutes() / (60.0 * 24.0);
        double halfLife = halfLifeDays(authorityLevel);
        double decayed = baseConfidence * Math.pow(0.5, elapsedDays / halfLife);
        return Math.max(0.0, decayed);
    }

    /** Whether a decayed confidence has fallen far enough to no longer be worth surfacing/keeping as a live candidate. */
    public static boolean isStale(double effectiveConfidence) {
        return effectiveConfidence < DISMISS_THRESHOLD;
    }
}
