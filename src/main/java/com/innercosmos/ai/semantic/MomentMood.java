package com.innercosmos.ai.semantic;

import java.util.ArrayList;
import java.util.List;

/**
 * IC-EMO-002 value object: the "此刻情绪" (current-moment mood) read derived from
 * the LATEST enriched {@link com.innercosmos.entity.EmotionTrace} of a user.
 *
 * <p>This is the single shared shape produced by
 * {@link com.innercosmos.service.EmotionInsightService#latestMood(Long)} and consumed
 * by both the Aurora context assembler (real-time perception fed into the prompt) and
 * the {@code GET /api/aurora/mood} endpoint (energy-orb on the frontend). Keeping it in
 * one place avoids the two consumers re-implementing the defensive spectrum parsing.
 *
 * <p>It always represents a well-formed read:
 * <ul>
 *   <li>{@link #present} is {@code false} when the user has no trace yet — callers then
 *       surface a gentle empty state instead of fabricating a mood;</li>
 *   <li>the {@link #spectrum} may be empty (old Phase-1 rows or malformed JSON) but is
 *       never null;</li>
 *   <li>{@link #weatherType} falls back to {@code "CLEAR"} when unknown.</li>
 * </ul>
 */
public class MomentMood {

    /** Weather type surfaced when no trace / unknown weather is available. */
    public static final String NEUTRAL_WEATHER = "CLEAR";

    /** True when this read is backed by a real EmotionTrace; false = no-data fallback. */
    public boolean present;
    /** Primary emotion name, e.g. "平静"; null/blank when {@link #present} is false. */
    public String primaryEmotion;
    /** Intensity on the 0..10 scale (0 when absent). */
    public double intensity;
    /** Top spectrum entries (emotion + ratio), never null, possibly empty. */
    public List<EmotionInsight.SpectrumEntry> spectrum = new ArrayList<>();
    /** Derived emotion weather, e.g. "SUNNY"/"RAINY"; {@link #NEUTRAL_WEATHER} when unknown. */
    public String weatherType = NEUTRAL_WEATHER;

    /**
     * The compact "此刻情绪" perception string: primary emotion + a brief top-2
     * spectrum in a single paren group, e.g. "平静（平静 60% · 期待 30%）". Falls back to
     * emotion-only, and to a gentle empty-state sentence when {@link #present} is false.
     * Never null.
     */
    public String momentLabel = "";

    /** A no-data, well-formed read with a gentle empty-state label. */
    public static MomentMood absent(String gentleLabel) {
        MomentMood m = new MomentMood();
        m.present = false;
        m.primaryEmotion = null;
        m.intensity = 0.0;
        m.weatherType = NEUTRAL_WEATHER;
        m.momentLabel = gentleLabel == null ? "" : gentleLabel;
        return m;
    }
}
