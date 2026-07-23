package com.innercosmos.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import com.innercosmos.dto.ChatRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Gemini audit 2.8 (PARTIAL/P3, see docs/audit/2026-07-23-gemini-master-audit-reconciliation.md
 * section R2): the report's "unbounded leakage" characterization was wrong (this store is
 * maxEntries-bounded and only ever active on the Redis-disabled/dev path), but the real defect
 * underneath was genuine -- purgeExpired() only ever ran from inside stage(), so a token that
 * expired and was never followed by *another* stage() call sat resident in memory until unrelated
 * traffic happened to trigger one. This suite proves the idle-expiry gap against the pre-fix code
 * (no scheduled sweep, no injectable Clock) and pins the fix: a Clock the test can advance, and a
 * sweep that reclaims expired entries with zero further stage()/consume() traffic.
 */
class InMemoryAuroraStreamStageStoreTest {

    @Test
    void idleExpiredStageIsSweptWithoutAnyFurtherStageOrConsumeCall() {
        Instant start = Instant.parse("2026-07-23T00:00:00Z");
        InMemoryAuroraStreamStageStore store = new InMemoryAuroraStreamStageStore(Duration.ofMillis(10), 1024);
        store.useClock(Clock.fixed(start, ZoneOffset.UTC));

        store.stage(1L, new ChatRequest());
        assertThat(store.size()).isEqualTo(1);

        // Long idle: nobody stages or consumes anything else, on this token or any other -- the
        // only thing that happens is time passing well past the TTL.
        store.useClock(Clock.fixed(start.plus(Duration.ofMinutes(10)), ZoneOffset.UTC));

        // The store must be able to reclaim its own memory via the scheduled sweep -- this must
        // not require a fresh stage()/consume() call on some unrelated token to happen to trigger
        // the old inline purge.
        store.cleanupExpired();

        assertThat(store.size()).isZero();
    }

    @Test
    void freshStageWithinTtlSurvivesASweep() {
        Instant start = Instant.parse("2026-07-23T00:00:00Z");
        InMemoryAuroraStreamStageStore store = new InMemoryAuroraStreamStageStore(Duration.ofMinutes(1), 1024);
        store.useClock(Clock.fixed(start, ZoneOffset.UTC));

        String token = store.stage(1L, new ChatRequest());
        store.cleanupExpired();

        assertThat(store.size()).isEqualTo(1);
        assertThat(store.consume(1L, token)).isNotNull();
    }

    @Test
    void shutdownClearsAllResidentStagesRegardlessOfTtl() {
        InMemoryAuroraStreamStageStore store = new InMemoryAuroraStreamStageStore(Duration.ofMinutes(5), 1024);
        store.stage(1L, new ChatRequest());
        store.stage(2L, new ChatRequest());
        assertThat(store.size()).isEqualTo(2);

        store.clear();

        assertThat(store.size()).isZero();
    }
}
