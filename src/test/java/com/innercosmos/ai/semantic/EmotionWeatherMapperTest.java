package com.innercosmos.ai.semantic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * IC-EMO-001: {@link EmotionWeatherMapper} is the single deterministic
 * (primaryEmotion, intensity) -> weatherType matrix that replaces the scattered
 * EMOTION_WEATHER maps and intensity-only inferWeather helpers.
 */
class EmotionWeatherMapperTest {

    @Test
    @DisplayName("Known emotion names map to their canonical weather regardless of intensity")
    void knownEmotions_mapToCanonicalWeather() {
        assertEquals("FOGGY", EmotionWeatherMapper.weatherFor("焦虑", 5.0));
        assertEquals("RAINY", EmotionWeatherMapper.weatherFor("自责", 5.0));
        assertEquals("STORM", EmotionWeatherMapper.weatherFor("沮丧", 5.0));
        assertEquals("STORM", EmotionWeatherMapper.weatherFor("愤怒", 5.0));
        assertEquals("SUNNY", EmotionWeatherMapper.weatherFor("喜悦", 5.0));
        assertEquals("CLOUDY", EmotionWeatherMapper.weatherFor("疲惫", 5.0));
    }

    @Test
    @DisplayName("Settlement-style sentiment labels also resolve to a canonical weather")
    void sentimentLabelEmotions_mapToWeather() {
        assertEquals("STORM", EmotionWeatherMapper.weatherFor("危机", 9.0));
        assertEquals("SUNNY", EmotionWeatherMapper.weatherFor("积极", 4.0));
        assertEquals("SUNNY", EmotionWeatherMapper.weatherFor("开心", 4.0));
    }

    @Test
    @DisplayName("High intensity escalates an otherwise mild/unknown emotion to STORM")
    void highIntensity_escalatesUnknownEmotion() {
        // Unknown emotion at very high intensity should escalate, not stay neutral.
        assertEquals("STORM", EmotionWeatherMapper.weatherFor("复杂", 9.0));
    }

    @Test
    @DisplayName("Unknown emotion falls back to the intensity matrix")
    void unknownEmotion_usesIntensityMatrix() {
        assertEquals("STORM", EmotionWeatherMapper.weatherForIntensity(7.0));
        assertEquals("RAINY", EmotionWeatherMapper.weatherForIntensity(5.0));
        assertEquals("CLOUDY", EmotionWeatherMapper.weatherForIntensity(3.0));
        assertEquals("SUNNY", EmotionWeatherMapper.weatherForIntensity(1.0));
        assertEquals("SUNNY", EmotionWeatherMapper.weatherForIntensity(0.0));
    }

    @Test
    @DisplayName("Null / blank emotion and null intensity never NPE and yield a sane weather")
    void nullInputs_areSafe() {
        assertNotNull(EmotionWeatherMapper.weatherFor(null, null));
        assertNotNull(EmotionWeatherMapper.weatherFor("", null));
        assertNotNull(EmotionWeatherMapper.weatherFor("焦虑", null));
        assertEquals("SUNNY", EmotionWeatherMapper.weatherForIntensity(null));
    }

    @Test
    @DisplayName("Mapping is deterministic for repeated calls")
    void deterministic() {
        for (int i = 0; i < 5; i++) {
            assertEquals("FOGGY", EmotionWeatherMapper.weatherFor("焦虑", 6.0));
        }
    }
}
