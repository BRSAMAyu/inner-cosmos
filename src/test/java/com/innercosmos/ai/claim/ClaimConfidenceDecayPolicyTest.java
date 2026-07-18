package com.innercosmos.ai.claim;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure/deterministic — no Spring context, mirrors the {@code DualKernelBudgetPolicyTest} pattern.
 * Pins the TRACK-A-LIVING-INTELLIGENCE.md §6 requirement: weak inferences decay, explicit user
 * assertions never do.
 */
class ClaimConfidenceDecayPolicyTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Test
    void explicitUserAssertionsNeverDecayRegardlessOfElapsedTime() {
        assertTrue(ClaimConfidenceDecayPolicy.neverDecays("USER_CORRECTION"));
        assertTrue(ClaimConfidenceDecayPolicy.neverDecays("USER_CONFIRMED"));

        double afterAYear = ClaimConfidenceDecayPolicy.effectiveConfidence(
                1.0, "USER_CORRECTION", T0, T0.plusYears(1));
        assertEquals(1.0, afterAYear, 1e-9, "an explicit correction must not fade after a year");
    }

    @Test
    void modelInferenceDecaysFasterThanRepeatedExplicit() {
        LocalDateTime after30Days = T0.plusDays(30);
        double inference = ClaimConfidenceDecayPolicy.effectiveConfidence(0.6, ClaimAuthority.MODEL_INFERENCE, T0, after30Days);
        double repeatedExplicit = ClaimConfidenceDecayPolicy.effectiveConfidence(0.6, ClaimAuthority.REPEATED_EXPLICIT, T0, after30Days);
        assertTrue(inference < repeatedExplicit,
                "a pure model guess must decay faster than a repeated explicit statement over the same window: "
                        + inference + " vs " + repeatedExplicit);
    }

    @Test
    void halfLifeOrderingMatchesEvidenceStrength() {
        // Weakest evidence -> shortest half-life; strongest (still-unconfirmed) evidence -> longest.
        double modelInference = ClaimConfidenceDecayPolicy.halfLifeDays(ClaimAuthority.MODEL_INFERENCE);
        double singleExplicit = ClaimConfidenceDecayPolicy.halfLifeDays(ClaimAuthority.SINGLE_EXPLICIT);
        double repeatedBehavior = ClaimConfidenceDecayPolicy.halfLifeDays(ClaimAuthority.REPEATED_BEHAVIOR);
        double repeatedExplicit = ClaimConfidenceDecayPolicy.halfLifeDays(ClaimAuthority.REPEATED_EXPLICIT);
        assertTrue(modelInference < singleExplicit);
        assertTrue(singleExplicit < repeatedBehavior);
        assertTrue(repeatedBehavior < repeatedExplicit);
    }

    @Test
    void confidenceHalvesExactlyAtOneHalfLife() {
        double halfLife = ClaimConfidenceDecayPolicy.halfLifeDays(ClaimAuthority.SINGLE_EXPLICIT);
        LocalDateTime atHalfLife = T0.plusMinutes(Math.round(halfLife * 24 * 60));
        double effective = ClaimConfidenceDecayPolicy.effectiveConfidence(0.8, ClaimAuthority.SINGLE_EXPLICIT, T0, atHalfLife);
        assertEquals(0.4, effective, 0.01);
    }

    @Test
    void neverGoesNegativeAndFloorsAtZeroForLongElapsedTime() {
        double effective = ClaimConfidenceDecayPolicy.effectiveConfidence(
                0.5, ClaimAuthority.MODEL_INFERENCE, T0, T0.plusYears(5));
        assertTrue(effective >= 0.0);
        assertTrue(effective < 0.01);
    }

    @Test
    void reinforcementResetsTheClockBecauseCallerPassesTheLatestReferenceTime() {
        // Simulates ClaimCandidateServiceImpl#upsert bumping updatedAt on every re-mention: passing
        // a fresher referenceTime yields a higher effective confidence than an unreinforced claim of
        // the same age and base confidence.
        LocalDateTime now = T0.plusDays(20);
        double reinforcedRecently = ClaimConfidenceDecayPolicy.effectiveConfidence(
                0.6, ClaimAuthority.SINGLE_EXPLICIT, T0.plusDays(18), now);
        double neverReinforced = ClaimConfidenceDecayPolicy.effectiveConfidence(
                0.6, ClaimAuthority.SINGLE_EXPLICIT, T0, now);
        assertTrue(reinforcedRecently > neverReinforced);
    }

    @Test
    void isStaleMatchesTheDismissThreshold() {
        assertFalse(ClaimConfidenceDecayPolicy.isStale(ClaimConfidenceDecayPolicy.DISMISS_THRESHOLD + 0.01));
        assertTrue(ClaimConfidenceDecayPolicy.isStale(ClaimConfidenceDecayPolicy.DISMISS_THRESHOLD - 0.01));
    }

    @Test
    void nullOrNonPositiveElapsedTimeReturnsBaseConfidenceUnmodified() {
        assertEquals(0.5, ClaimConfidenceDecayPolicy.effectiveConfidence(0.5, ClaimAuthority.MODEL_INFERENCE, null, T0));
        assertEquals(0.5, ClaimConfidenceDecayPolicy.effectiveConfidence(0.5, ClaimAuthority.MODEL_INFERENCE, T0, T0));
        assertEquals(0.5, ClaimConfidenceDecayPolicy.effectiveConfidence(0.5, ClaimAuthority.MODEL_INFERENCE, T0, T0.minusDays(1)));
    }

    @Test
    void unknownAuthorityLevelFallsBackToADefaultWeakDecayInsteadOfCrashing() {
        double effective = ClaimConfidenceDecayPolicy.effectiveConfidence(1.0, "SOMETHING_UNEXPECTED", T0, T0.plusDays(21));
        assertEquals(0.5, effective, 0.01, "default half-life is 21 days");
    }
}
