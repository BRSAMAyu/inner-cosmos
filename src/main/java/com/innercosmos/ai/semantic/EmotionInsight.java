package com.innercosmos.ai.semantic;

import com.innercosmos.entity.EmotionTrace;

import java.util.ArrayList;
import java.util.List;

/**
 * IC-EMO-001 value object: the single, source-tagged emotion reading produced by
 * {@link com.innercosmos.service.EmotionInsightService} for a piece of user text.
 *
 * <p>It carries the primary emotion, a clamped 0..10 intensity score, a small
 * normalized emotion {@link #spectrum}, the trigger scene, the derived weather
 * type and the {@link #analysisSource} that produced it ("LLM" | "LEXICON" |
 * "SETTLEMENT"). It is a plain field-style carrier (matching the project's entity
 * style) — it performs no persistence itself.
 */
public class EmotionInsight {

    /** Allowed values for {@link #analysisSource}. */
    public static final String SOURCE_LLM = "LLM";
    public static final String SOURCE_LEXICON = "LEXICON";
    public static final String SOURCE_SETTLEMENT = "SETTLEMENT";

    public String primaryEmotion;
    /** Intensity on the 0..10 scale (clamped at construction time via {@link #clampScore}). */
    public double emotionScore;
    public List<SpectrumEntry> spectrum = new ArrayList<>();
    public String triggerScene;
    public String weatherType;
    public String analysisSource;

    public EmotionInsight() {
    }

    /**
     * A single emotion / ratio pair in the {@link #spectrum}. Ratios across the
     * spectrum are normalized to sum to ~1.0.
     */
    public static class SpectrumEntry {
        public String emotion;
        public double ratio;

        public SpectrumEntry() {
        }

        public SpectrumEntry(String emotion, double ratio) {
            this.emotion = emotion;
            this.ratio = ratio;
        }
    }

    /**
     * Clamp a raw score into the EmotionTrace [0,10] range. Centralized here so
     * every construction site enforces the same invariant the persistence layer
     * does (see {@link EmotionTrace#clampScore}).
     */
    public static double clampScore(double raw) {
        if (raw < EmotionTrace.MIN_SCORE) {
            return EmotionTrace.MIN_SCORE;
        }
        if (raw > EmotionTrace.MAX_SCORE) {
            return EmotionTrace.MAX_SCORE;
        }
        return raw;
    }
}
