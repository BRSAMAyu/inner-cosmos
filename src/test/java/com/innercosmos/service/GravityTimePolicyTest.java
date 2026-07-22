package com.innercosmos.service;

import com.innercosmos.entity.MemoryCard;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gemini audit 1.5 (CONFIRMED/P1): GravityRecalculateListener and NightlyMemorySettlementJob
 * used to each inline their own "days since last touch" calculation with DIFFERENT fallbacks
 * for a missing lastTouchedAt (listener: hardcoded 30 days; nightly job: createdAt, or 0 if even
 * that was missing) -- so the same card's gravity could jump depending on which path last ran.
 * This pins the single shared policy both now call into. useClock() is package-private, callable
 * directly since this test lives in the same package.
 */
class GravityTimePolicyTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private GravityTimePolicy policyAt(LocalDateTime now) {
        GravityTimePolicy policy = new GravityTimePolicy();
        policy.useClock(Clock.fixed(now.atZone(ZONE).toInstant(), ZONE));
        return policy;
    }

    private MemoryCard card(LocalDateTime lastTouchedAt, LocalDateTime createdAt) {
        MemoryCard card = new MemoryCard();
        card.lastTouchedAt = lastTouchedAt;
        card.createdAt = createdAt;
        return card;
    }

    @Test
    void usesLastTouchedAtWhenPresent() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 23, 0, 0);
        GravityTimePolicy policy = policyAt(now);
        MemoryCard card = card(now.minusDays(5), now.minusDays(100)); // createdAt would give a very different answer

        assertEquals(5L, policy.daysSinceAnchor(card));
    }

    @Test
    void fallsBackToCreatedAtWhenNeverTouched_notAHardcoded30() {
        // Regression: the old listener hardcoded 30 days for this exact case (lastTouchedAt
        // null); the correct, now-unified fallback is createdAt's actual age.
        LocalDateTime now = LocalDateTime.of(2026, 7, 23, 0, 0);
        GravityTimePolicy policy = policyAt(now);
        MemoryCard card = card(null, now.minusDays(7));

        assertEquals(7L, policy.daysSinceAnchor(card));
    }

    @Test
    void zeroWhenNeitherTimestampExists() {
        GravityTimePolicy policy = policyAt(LocalDateTime.now());
        MemoryCard card = card(null, null);

        assertEquals(0L, policy.daysSinceAnchor(card));
    }

    @Test
    void neverNegative_evenIfAnchorIsSomehowInTheFuture() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 23, 0, 0);
        GravityTimePolicy policy = policyAt(now);
        MemoryCard card = card(now.plusDays(3), null);

        assertEquals(0L, policy.daysSinceAnchor(card));
    }
}
