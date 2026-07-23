package com.innercosmos.safety;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gemini audit 3.9 (CONFIRMED/P1): session-scoped, time-decaying, explainable multi-turn risk
 * state, ADDED on top of (never replacing) the existing per-message crisis/abuse detection.
 */
class SessionRiskAggregatorTest {

    /** A Clock whose instant can be advanced mid-test, for real decay/recovery assertions. */
    private static Clock mutableClock(AtomicReference<Instant> now) {
        return new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
    }

    @Test
    @DisplayName("3.9: several MEDIUM signals in one session, close together, escalate even though no single message alone would")
    void gradualEscalation_acrossSeveralTurns_eventuallyEscalates() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-07-23T10:00:00Z"));
        SessionRiskAggregator aggregator = new SessionRiskAggregator(mutableClock(now));
        Long sessionId = 1L;

        var first = aggregator.observe(sessionId, "MEDIUM", "今天感觉很难过");
        assertFalse(first.escalate(), "a single MEDIUM signal alone must not escalate");

        var second = aggregator.observe(sessionId, "MEDIUM", "还是觉得很难过，撑不下去了");
        var third = aggregator.observe(sessionId, "MEDIUM", "真的觉得撑不住了");

        assertTrue(third.escalate(),
                "repeated MEDIUM signals within a session must eventually escalate the effective risk level");
        assertNotNull(third.reason(), "an escalation must carry an explainable, category-level reason");
    }

    @Test
    @DisplayName("3.9: a negated/past-tense expression contributes far less than a present-tense one")
    void negatedContext_contributesLessThanPresentTense() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-07-23T10:00:00Z"));
        SessionRiskAggregator negatedAggregator = new SessionRiskAggregator(mutableClock(now));
        SessionRiskAggregator presentAggregator = new SessionRiskAggregator(mutableClock(now));
        Long sessionId = 2L;

        // Three negated/past-tense MEDIUM signals must NOT reach the same escalation a present
        // -tense repetition would.
        negatedAggregator.observe(sessionId, "MEDIUM", "我以前也曾经觉得很绝望");
        negatedAggregator.observe(sessionId, "MEDIUM", "曾经有过那种念头，但已经不这样想了");
        var negatedThird = negatedAggregator.observe(sessionId, "MEDIUM", "过去确实很难熬");

        presentAggregator.observe(sessionId, "MEDIUM", "现在觉得很绝望");
        presentAggregator.observe(sessionId, "MEDIUM", "还是有那种念头");
        var presentThird = presentAggregator.observe(sessionId, "MEDIUM", "真的很难熬");

        assertFalse(negatedThird.escalate(),
                "negated/past-tense repetition must contribute far less and not reach escalation");
        assertTrue(presentThird.escalate(),
                "the same repetition WITHOUT negation must escalate (control case)");
    }

    @Test
    @DisplayName("3.9: quoting someone else's words does not count as the user's own risk signal")
    void thirdPartyQuote_doesNotCountAsUsersOwnSignal() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-07-23T10:00:00Z"));
        SessionRiskAggregator aggregator = new SessionRiskAggregator(mutableClock(now));
        Long sessionId = 3L;

        for (int i = 0; i < 5; i++) {
            var result = aggregator.observe(sessionId, "MEDIUM",
                    "他说他有一次也觉得很绝望，我只是转述一下他跟我说的话");
            assertFalse(result.escalate(),
                    "quoting a third party's words must never accumulate toward the user's own escalation");
        }
    }

    @Test
    @DisplayName("3.9: risk recovers (decays back down) as calm turns pass over real elapsed time")
    void risk_recoversAsTimePasses() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-07-23T10:00:00Z"));
        SessionRiskAggregator aggregator = new SessionRiskAggregator(mutableClock(now));
        Long sessionId = 4L;

        aggregator.observe(sessionId, "MEDIUM", "今天很难受");
        aggregator.observe(sessionId, "MEDIUM", "还是很难受");
        var escalated = aggregator.observe(sessionId, "MEDIUM", "真的撑不住了");
        assertTrue(escalated.escalate(), "escalated after 3 close-together MEDIUM signals");

        // Advance well past several half-lives (10 min each) with no further signals.
        now.set(now.get().plus(Duration.ofHours(2)));

        var afterRecovery = aggregator.observe(sessionId, "LOW", "今天天气还不错");
        assertFalse(afterRecovery.escalate(),
                "after a long calm gap, the session must have recovered -- one LOW signal must not re-escalate");
    }

    @Test
    @DisplayName("3.9: a session with no tracking key (null sessionId) never escalates")
    void nullSessionId_neverEscalates() {
        SessionRiskAggregator aggregator = new SessionRiskAggregator(Clock.systemUTC());
        var result = aggregator.observe(null, "HIGH", "anything");
        assertFalse(result.escalate());
    }
}
