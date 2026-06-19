package com.innercosmos.ai.semantic;

import java.util.Map;

/**
 * IC-EMO-001: the single deterministic {@code (primaryEmotion, intensity) ->
 * weatherType} matrix.
 *
 * <p>This REPLACES the scattered emotion-name weather maps (the listener's
 * {@code EMOTION_WEATHER}) and the intensity-only {@code inferWeather} helpers
 * that lived in the settlement/memory services. Resolution order:
 * <ol>
 *   <li>a very high intensity always escalates to {@code STORM} (a strong signal
 *       should never be reported as calm weather);</li>
 *   <li>otherwise a known emotion name resolves to its canonical weather;</li>
 *   <li>otherwise we fall back to the intensity matrix (preserving the prior
 *       {@code inferWeather} thresholds exactly).</li>
 * </ol>
 * Pure, side-effect free and null-safe so it is trivially unit-testable.
 */
public final class EmotionWeatherMapper {

    private EmotionWeatherMapper() {
    }

    /** Intensity at/above which any emotion escalates to STORM. */
    private static final double STORM_INTENSITY = 8.0;

    /**
     * Canonical emotion-name -> weather. Covers both the listener's 6-keyword
     * vocabulary and the settlement sentiment-label vocabulary so a single map is
     * authoritative for every producer.
     */
    private static final Map<String, String> EMOTION_WEATHER = Map.ofEntries(
            // Listener 6-keyword vocabulary
            Map.entry("焦虑", "FOGGY"),
            Map.entry("自责", "RAINY"),
            Map.entry("沮丧", "STORM"),
            Map.entry("愤怒", "STORM"),
            Map.entry("喜悦", "SUNNY"),
            Map.entry("疲惫", "CLOUDY"),
            // Settlement sentiment-label vocabulary
            Map.entry("危机", "STORM"),
            Map.entry("难过", "RAINY"),
            Map.entry("负面", "CLOUDY"),
            Map.entry("积极", "SUNNY"),
            Map.entry("平静", "SUNNY"),
            // Common lexicon emotion names
            Map.entry("开心", "SUNNY"),
            Map.entry("高兴", "SUNNY"),
            Map.entry("孤独", "RAINY"),
            Map.entry("烦躁", "FOGGY"),
            Map.entry("委屈", "RAINY"));

    /**
     * Resolve the weather for an emotion name + intensity.
     *
     * @param primaryEmotion emotion label (may be null/blank/unknown)
     * @param intensity      0..10 intensity (may be null)
     * @return a non-null weather type
     */
    public static String weatherFor(String primaryEmotion, Double intensity) {
        double value = intensity == null ? 0.0 : intensity;
        if (value >= STORM_INTENSITY) {
            return "STORM";
        }
        if (primaryEmotion != null && !primaryEmotion.isBlank()) {
            String mapped = EMOTION_WEATHER.get(primaryEmotion.trim());
            if (mapped != null) {
                return mapped;
            }
        }
        return weatherForIntensity(intensity);
    }

    /**
     * Intensity-only weather, preserving the historical {@code inferWeather}
     * thresholds (>=7 STORM, >=5 RAINY, >=3 CLOUDY, else SUNNY).
     */
    public static String weatherForIntensity(Double intensity) {
        double value = intensity == null ? 0.0 : intensity;
        if (value >= 7) return "STORM";
        if (value >= 5) return "RAINY";
        if (value >= 3) return "CLOUDY";
        return "SUNNY";
    }
}
