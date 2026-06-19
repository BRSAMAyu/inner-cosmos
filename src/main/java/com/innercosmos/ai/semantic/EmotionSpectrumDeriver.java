package com.innercosmos.ai.semantic;

import com.innercosmos.ai.semantic.PseudoSemanticAnalyzer.AnalysisResult;

import java.util.ArrayList;
import java.util.List;

/**
 * IC-EMO-001: deterministically derives a small normalized emotion spectrum from
 * a {@link AnalysisResult}.
 *
 * <p>The spectrum is a short ordered list of {@code {emotion, ratio}} entries
 * whose ratios are non-negative and sum to ~1.0, with the dominant emotion first.
 * It is built purely from the lexicon analysis (sentiment label, detected themes,
 * intensity) so the same input always yields the same spectrum — keeping mock
 * mode deterministic.
 */
public final class EmotionSpectrumDeriver {

    private EmotionSpectrumDeriver() {
    }

    /**
     * Derive a normalized spectrum from a lexicon analysis. Null-safe: a null
     * analysis yields a single neutral "平静" entry at ratio 1.0.
     */
    public static List<EmotionInsight.SpectrumEntry> derive(AnalysisResult analysis) {
        List<EmotionInsight.SpectrumEntry> raw = new ArrayList<>();
        if (analysis == null) {
            raw.add(new EmotionInsight.SpectrumEntry("平静", 1.0));
            return normalize(raw);
        }

        String label = analysis.sentimentLabel == null ? "NEUTRAL" : analysis.sentimentLabel;
        List<String> themes = analysis.detectedThemes == null ? List.of() : analysis.detectedThemes;
        // Intensity (0..10) scales the dominant weight so stronger signals are
        // more concentrated; clamp into a sensible band.
        double intensity = Math.max(0.0, Math.min(10.0, analysis.intensityScore));
        double dominantWeight = 3.0 + intensity * 0.4; // ~3..7

        // Dominant emotion from sentiment label.
        String dominant = dominantEmotion(label, themes);
        raw.add(new EmotionInsight.SpectrumEntry(dominant, dominantWeight));

        // Secondary emotions from detected themes (deterministic order from the
        // analyzer's list), each contributing a smaller fixed weight.
        for (String theme : themes) {
            String secondary = themeEmotion(theme);
            if (secondary != null && !secondary.equals(dominant)) {
                raw.add(new EmotionInsight.SpectrumEntry(secondary, 2.0));
            }
        }

        // Always leave a small "其他" remainder so the spectrum reads as a blend.
        raw.add(new EmotionInsight.SpectrumEntry("其他", 1.0));

        return normalize(raw);
    }

    private static String dominantEmotion(String label, List<String> themes) {
        switch (label) {
            case "CRISIS":
                return "危机";
            case "NEGATIVE":
                if (themes.contains("情绪承压")) return "焦虑";
                if (themes.contains("关系牵动")) return "难过";
                if (themes.contains("自我评价")) return "自责";
                return "低落";
            case "POSITIVE":
                return "喜悦";
            default:
                return "平静";
        }
    }

    private static String themeEmotion(String theme) {
        switch (theme) {
            case "任务压力":
                return "疲惫";
            case "关系牵动":
                return "委屈";
            case "情绪承压":
                return "焦虑";
            case "自我评价":
                return "自责";
            case "希望期待":
                return "期待";
            case "认知探索":
                return "困惑";
            default:
                return null;
        }
    }

    /**
     * Normalize raw weights into ratios summing to 1.0, preserving order and
     * merging duplicate emotion labels. Deterministic.
     */
    private static List<EmotionInsight.SpectrumEntry> normalize(List<EmotionInsight.SpectrumEntry> raw) {
        // Merge duplicates by first-seen order.
        List<EmotionInsight.SpectrumEntry> merged = new ArrayList<>();
        for (EmotionInsight.SpectrumEntry entry : raw) {
            EmotionInsight.SpectrumEntry existing = null;
            for (EmotionInsight.SpectrumEntry m : merged) {
                if (m.emotion.equals(entry.emotion)) {
                    existing = m;
                    break;
                }
            }
            if (existing != null) {
                existing.ratio += entry.ratio;
            } else {
                merged.add(new EmotionInsight.SpectrumEntry(entry.emotion, entry.ratio));
            }
        }

        double total = 0;
        for (EmotionInsight.SpectrumEntry m : merged) {
            total += m.ratio;
        }
        if (total <= 0) {
            merged.clear();
            merged.add(new EmotionInsight.SpectrumEntry("平静", 1.0));
            total = 1.0;
        }

        // Round to 4 decimals deterministically, then push any rounding residual
        // onto the dominant (first) entry so the ratios sum to exactly 1.0.
        double assigned = 0;
        for (int i = 1; i < merged.size(); i++) {
            double r = Math.round((merged.get(i).ratio / total) * 10000.0) / 10000.0;
            merged.get(i).ratio = r;
            assigned += r;
        }
        merged.get(0).ratio = Math.round((1.0 - assigned) * 10000.0) / 10000.0;

        return merged;
    }
}
