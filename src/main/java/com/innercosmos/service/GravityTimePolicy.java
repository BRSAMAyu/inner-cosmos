package com.innercosmos.service;

import com.innercosmos.entity.MemoryCard;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Single canonical anchor/clock/rounding policy for gravity time-decay.
 *
 * Regression (Gemini audit 1.5, P1): GravityRecalculationServiceImpl (event-driven) and
 * NightlyMemorySettlementJob (nightly batch) used to each inline their own copy of the
 * "days since last touch" calculation with DIFFERENT fallbacks for a missing lastTouchedAt --
 * the listener hardcoded 30 days, the nightly job fell back to createdAt (or 0 if even that
 * was missing). Because the two paths disagreed on the anchor, the same card's gravity could
 * visibly jump depending on which path last recomputed it. Both callers now go through this one
 * policy, and the days-since-anchor value is rounded consistently (whole days via Duration,
 * matching the nightly job's prior ChronoUnit.DAYS behavior -- that was the more principled of
 * the two, so it becomes the one true rule instead of the listener's arbitrary "30").
 */
@Component
public class GravityTimePolicy {
    // system-default-zone, not UTC: both prior implementations called bare LocalDateTime.now()
    // (system default zone), and lastTouchedAt/createdAt are persisted in that same zone --
    // matching it here avoids a spurious multi-hour skew in "days since anchor".
    private Clock clock = Clock.systemDefaultZone();

    /** Test-only override so tests can pin "now" instead of racing the wall clock. */
    void useClock(Clock fixedClock) {
        this.clock = fixedClock;
    }

    /**
     * Days since the card was last meaningfully touched. Anchor priority: lastTouchedAt, then
     * createdAt, then 0 (the card has no usable timestamp at all -- treated as "just created").
     */
    public long daysSinceAnchor(MemoryCard card) {
        LocalDateTime anchor = card.lastTouchedAt != null ? card.lastTouchedAt : card.createdAt;
        if (anchor == null) return 0L;
        LocalDateTime now = LocalDateTime.now(clock);
        return Math.max(0L, Duration.between(anchor, now).toDays());
    }
}
