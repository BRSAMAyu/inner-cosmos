package com.innercosmos.service.memory;

import com.innercosmos.entity.MemoryCard;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CP-21 duplicate-event dedup. Settlement extracts one memory card per finished session, but
 * the SAME event re-told in a later session used to produce a second, parallel ACTIVE card —
 * two "current" memories of one event, no recurrence signal, no version trail. This matcher
 * recognizes a re-told event by near-identical extraction summaries (deterministic character
 * bigram cosine — the same cheap, explainable signal the retrieval scorer uses) so the
 * settlement pipeline can fold the repetition into the existing card instead of duplicating it.
 *
 * <p>Pure and stateless: candidates are supplied by the caller, who owns the status/tombstone
 * gating (only current, non-retracted memories may absorb a recurrence — reinforcing a
 * forgotten memory would be resurrection).
 */
public final class MemoryRecurrenceMatcher {

    /**
     * Cosine at or above this treats the two summaries as the same re-told event. Calibrated
     * between the two observed clusters: different events sharing a topic score well below
     * 0.3, while a re-told event (even paraphrased) scores 0.6+; 0.60 keeps margin on both
     * sides — a false MERGE rewrites a memory's identity, so the cut stays conservative.
     */
    public static final double EVENT_MATCH_THRESHOLD = 0.60;

    private MemoryRecurrenceMatcher() {
    }

    /** The best candidate whose summary matches the new extraction, or null when none does. */
    public static MemoryCard match(List<MemoryCard> candidates, String newSummary) {
        if (candidates == null || candidates.isEmpty() || newSummary == null || newSummary.isBlank()) {
            return null;
        }
        MemoryCard best = null;
        double bestScore = EVENT_MATCH_THRESHOLD;
        for (MemoryCard candidate : candidates) {
            if (candidate == null || candidate.id == null || candidate.summary == null) continue;
            double score = similarity(candidate.summary, newSummary);
            if (score >= bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    /** Deterministic character-bigram cosine similarity in [0, 1]; empty input scores 0. */
    public static double similarity(String left, String right) {
        Map<String, Double> a = bigrams(left);
        Map<String, Double> b = bigrams(right);
        if (a.isEmpty() || b.isEmpty()) return 0;
        double dot = 0;
        double aa = 0;
        double bb = 0;
        for (double v : a.values()) aa += v * v;
        for (double v : b.values()) bb += v * v;
        for (Map.Entry<String, Double> entry : a.entrySet()) {
            dot += entry.getValue() * b.getOrDefault(entry.getKey(), 0.0);
        }
        return dot / Math.sqrt(aa * bb);
    }

    private static Map<String, Double> bigrams(String value) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (value == null) return result;
        String compact = value.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", "");
        int[] cps = compact.codePoints().toArray();
        int n = compact.codePointCount(0, compact.length()) < 2 ? 1 : 2;
        for (int i = 0; i <= cps.length - n; i++) {
            result.merge(new String(cps, i, n), 1.0, Double::sum);
        }
        return result;
    }
}
