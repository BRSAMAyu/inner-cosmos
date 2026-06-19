package com.innercosmos.ai.semantic;

/**
 * IC-EMO-003 value object: a user's mid-term emotional <b>baseline</b>, computed
 * deterministically from an N-day window of {@link com.innercosmos.entity.EmotionTrace}
 * rows via an EWMA (exponentially weighted moving average).
 *
 * <p>Where {@link MomentMood} is the jittery real-time "此刻情绪" read of the latest
 * single trace, this is the persistent/continuous timescale: it smooths recent
 * traces (more recent weighted more) into a dominant-emotion tendency plus an
 * intensity mean &amp; variance, derives a {@link #stabilityScore} (higher = steadier),
 * and renders a short human-readable {@link #baselineLabel}. It is the ONLY emotion
 * signal allowed to move the user portrait — a single extreme trace must not — so
 * the portrait stays coherent (anti-thrash, see Spec §2).
 *
 * <p>The computation is fully deterministic: the same input rows always yield the
 * exact same numeric fields. An "absent" baseline (no traces yet) is well-formed,
 * never null, and never throws — use {@link #absent(int)}.
 */
public class EmotionBaseline {

    /** True when this baseline is backed by at least one real trace. */
    public boolean present;
    /** Weighted-dominant emotion tendency over the window; null/blank when absent. */
    public String dominantEmotion;
    /** EWMA (recency-weighted) mean intensity on the 0..10 scale. */
    public double intensityMean;
    /** EWMA-weighted variance of intensity (>= 0). Lower = steadier. */
    public double intensityVariance;
    /** Stability in [0,1]: 1 = perfectly steady (zero variance), → 0 as variance grows. */
    public double stabilityScore;
    /** Number of traces that fed this baseline. */
    public int sampleCount;
    /** The rolling window size in days this baseline was computed over. */
    public int windowDays;
    /** Short human-readable summary, e.g. "近 14 日总体平稳偏积极". Never null. */
    public String baselineLabel = "";

    public EmotionBaseline() {
    }

    /**
     * A well-formed "no baseline yet" read over the given window. present=false,
     * neutral numerics, a gentle label. Never null.
     */
    public static EmotionBaseline absent(int windowDays) {
        EmotionBaseline b = new EmotionBaseline();
        b.present = false;
        b.dominantEmotion = null;
        b.intensityMean = 0.0;
        b.intensityVariance = 0.0;
        b.stabilityScore = 1.0; // no variance observed
        b.sampleCount = 0;
        b.windowDays = windowDays;
        b.baselineLabel = "暂无情绪基线";
        return b;
    }
}
