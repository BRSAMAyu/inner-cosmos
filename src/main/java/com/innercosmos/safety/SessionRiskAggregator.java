package com.innercosmos.safety;

import com.innercosmos.util.SafetyTextNormalizer;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Gemini audit 3.9 (CONFIRMED/P1): the existing per-message crisis/abuse detection
 * (SafetyBoundaryFilter / CrisisKeywordRule / AbuseKeywordRule / DistressSignalDetector, all
 * Unicode-hardened by commit 05f55fc) is single-message/stateless -- it has no notion that SEVERAL
 * lower-tier signals across one session can justify a gentle direct safety check-in. This class
 * never upgrades repeated medium distress into a crisis block: HIGH remains reserved for explicit
 * or semantically confirmed acute evidence.
 *
 * <p>Deliberately NOT a diagnosis: this holds a small numeric score and a timestamp per session,
 * nothing else. Nothing here is persisted to a database or written to a metrics/analytics sink --
 * callers (SafetyServiceImpl) must record only the resulting risk LEVEL/category (as the existing
 * SafetyEvent audit trail already does), never the raw text that produced it. This class itself
 * never logs the raw text either -- it only ever touches it transiently, in-memory, for this one
 * {@link #observe} call, to apply the negation/quoting adjustment below.
 *
 * <p>Time-decay: the score decays with an exponential half-life as real time passes (via the
 * injected {@link Clock}), so a session recovers as calmer turns go by -- this is deliberately
 * time-based, not just turn-count-based, so a long gap between turns also lets a session recover.
 *
 * <p>Context adjustment (deterministic, no LLM judge call): a negation/past-tense cue ("曾经",
 * "以前", "不再", "used to", "not anymore") reduces (never removes) a signal's contribution --
 * the person may still be worth watching, just not at full weight. A third-party-quote cue
 * ("他说", "她说", "TA说", "someone said", "he said") makes the signal NOT count as the user's own
 * expression at all.
 */
@Component
public class SessionRiskAggregator {

    private static final double HIGH_WEIGHT = 1.0;
    private static final double MEDIUM_WEIGHT = 0.45;
    private static final double LOW_WEIGHT = 0.0;
    /** Repeated medium evidence earns a gentle check-in, never an automatic crisis block. */
    private static final double GENTLE_CHECK_IN_THRESHOLD = 1.0;
    private static final Duration HALF_LIFE = Duration.ofMinutes(10);
    /** Opportunistic sweep of long-idle sessions so this map cannot grow unboundedly over uptime. */
    private static final Duration SWEEP_IDLE_RETENTION = Duration.ofHours(2);
    private static final long SWEEP_EVERY_N_CALLS = 500;
    private static final int MAX_OBSERVATIONS_PER_SESSION = 128;

    private static final Pattern NEGATION_OR_PAST = Pattern.compile(
            "曾经|以前|过去|不再|已经不|现在不会|used to|not anymore|no longer|not any more|used, not");
    private static final Pattern THIRD_PARTY_QUOTE = Pattern.compile(
            "他说|她说|ta说|别人说|朋友说|有人说|他跟我说|她跟我说|he said|she said|someone said|they said|according to");

    private final Clock clock;
    private final Map<Long, SessionState> sessions = new ConcurrentHashMap<>();
    private final AtomicLong callCount = new AtomicLong();

    public SessionRiskAggregator(Clock clock) {
        this.clock = clock;
    }

    /**
     * Feeds this turn's per-message risk level into the session's rolling state and reports
     * the legacy view of the rolling state. It deliberately never auto-escalates to HIGH.
     *
     * @param sessionId session key; {@code null} means "no session to track", never escalates
     * @param riskLevel this turn's own per-message risk level (LOW/MEDIUM/HIGH/NONE) as already
     *                  decided by the existing keyword/semantic detectors -- never re-decided here
     * @param text      this turn's raw text, used ONLY transiently in-process to detect a
     *                  negation/past-tense or third-party-quote context; never stored or logged
     */
    public Escalation observe(Long sessionId, String riskLevel, String text) {
        return observe(sessionId, null, riskLevel, text);
    }

    /**
     * Idempotent variant for a user turn that can pass through more than one synchronous gate
     * (for example Aurora's foreground acknowledgement followed by its authoritative stream).
     * Reusing the same observation id returns the first decision without adding the signal twice.
     */
    public Escalation observe(Long sessionId, String observationId, String riskLevel, String text) {
        Observation observation = observeState(sessionId, observationId, riskLevel, text);
        // Kept for callers compiled against the previous contract. Session patterns no longer
        // auto-escalate to HIGH; only explicit or semantically confirmed acute evidence may block.
        return new Escalation(false, observation.score(), observation.reason());
    }

    public Observation observeState(Long sessionId, String observationId, String riskLevel, String text) {
        maybeSweep();
        if (sessionId == null) {
            return Observation.normal();
        }
        double rawWeight = weightFor(riskLevel);
        // Ordinary LOW/NONE messages are not evidence of a concerning multi-turn pattern.
        // Avoid creating session state for them at all.
        if (rawWeight <= 0) {
            return Observation.normal();
        }
        SessionState state = sessions.computeIfAbsent(sessionId, id -> new SessionState());
        synchronized (state) {
            String stableObservationId = normalizeObservationId(observationId);
            if (stableObservationId != null) {
                Observation previous = state.observations.get(stableObservationId);
                if (previous != null) {
                    return previous;
                }
            }
            Instant now = Instant.now(clock);
            decay(state, now);
            double weight = adjustForContext(rawWeight, text);
            state.score += weight;
            state.lastUpdate = now;
            RiskState riskState;
            String reason = null;
            if ("HIGH".equals(riskLevel)) {
                riskState = RiskState.HIGH_CONFIRMED;
            } else if (weight <= 0) {
                riskState = RiskState.NORMAL;
            } else if (state.score >= GENTLE_CHECK_IN_THRESHOLD) {
                riskState = RiskState.GENTLE_CHECK_IN;
                reason = "repeated medium distress signals warrant a gentle direct safety check-in";
            } else {
                riskState = RiskState.DISTRESS_WATCH;
            }
            Observation result = new Observation(riskState, round(state.score), reason);
            if (stableObservationId != null) {
                state.observations.put(stableObservationId, result);
                while (state.observations.size() > MAX_OBSERVATIONS_PER_SESSION) {
                    String oldest = state.observations.keySet().iterator().next();
                    state.observations.remove(oldest);
                }
            }
            return result;
        }
    }

    private String normalizeObservationId(String observationId) {
        if (observationId == null || observationId.isBlank()) {
            return null;
        }
        String normalized = observationId.trim();
        return normalized.length() <= 128 ? normalized : normalized.substring(0, 128);
    }

    private void decay(SessionState state, Instant now) {
        if (state.lastUpdate == null) {
            return;
        }
        long elapsedMs = Duration.between(state.lastUpdate, now).toMillis();
        if (elapsedMs <= 0) {
            return;
        }
        double halfLives = elapsedMs / (double) HALF_LIFE.toMillis();
        state.score *= Math.pow(0.5, halfLives);
    }

    private double weightFor(String riskLevel) {
        if (riskLevel == null) {
            return 0.0;
        }
        return switch (riskLevel) {
            case "HIGH" -> HIGH_WEIGHT;
            case "MEDIUM" -> MEDIUM_WEIGHT;
            case "LOW" -> LOW_WEIGHT;
            default -> 0.0;
        };
    }

    /**
     * Reduces (negation/past-tense) or zeroes (third-party quote) a signal's contribution based
     * on a lightweight, deterministic reading of THIS turn's own text. Uses the same Unicode
     * -hardened normalization as the rest of the safety pipeline so a zero-width/full-width
     * obfuscation of these cues doesn't defeat them either.
     */
    private double adjustForContext(double weight, String text) {
        if (weight <= 0 || text == null || text.isBlank()) {
            return weight;
        }
        String normalized = SafetyTextNormalizer.normalizeForMatch(text);
        if (THIRD_PARTY_QUOTE.matcher(normalized).find()) {
            return 0.0;
        }
        if (NEGATION_OR_PAST.matcher(normalized).find()) {
            return weight * 0.2;
        }
        return weight;
    }

    private void maybeSweep() {
        if (callCount.incrementAndGet() % SWEEP_EVERY_N_CALLS != 0) {
            return;
        }
        Instant now = Instant.now(clock);
        sessions.entrySet().removeIf(entry -> {
            SessionState state = entry.getValue();
            synchronized (state) {
                return state.lastUpdate == null
                        || Duration.between(state.lastUpdate, now).compareTo(SWEEP_IDLE_RETENTION) > 0;
            }
        });
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static final class SessionState {
        double score;
        Instant lastUpdate;
        final Map<String, Observation> observations = new LinkedHashMap<>();
    }

    /**
     * @param escalate whether the accumulated session pattern should escalate this turn's
     *                 effective risk level
     * @param score    the (rounded) accumulated score, for observability only -- never logged
     *                 alongside raw text
     * @param reason   a short, category-level explanation (never raw text), null when not escalating
     */
    public record Escalation(boolean escalate, double score, String reason) {
        static Escalation none() {
            return new Escalation(false, 0.0, null);
        }
    }

    public enum RiskState {
        NORMAL,
        DISTRESS_WATCH,
        GENTLE_CHECK_IN,
        HIGH_CONFIRMED
    }

    public record Observation(RiskState state, double score, String reason) {
        public boolean gentleCheckIn() {
            return state == RiskState.GENTLE_CHECK_IN;
        }

        static Observation normal() {
            return new Observation(RiskState.NORMAL, 0.0, null);
        }
    }
}
