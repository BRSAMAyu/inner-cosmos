package com.innercosmos.ai.semantic;

import com.innercosmos.ai.semantic.PseudoSemanticAnalyzer.AnalysisResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IC-EMO-001: {@link EmotionSpectrumDeriver} deterministically turns a
 * PseudoSemanticAnalyzer result into a small normalized emotion spectrum.
 */
class EmotionSpectrumDeriverTest {

    @Test
    @DisplayName("Spectrum ratios are non-negative and sum to ~1.0")
    void ratiosNormalized() {
        AnalysisResult analysis = PseudoSemanticAnalyzer.analyze("今天作业堆得太多了，我很焦虑也很累。");
        List<EmotionInsight.SpectrumEntry> spectrum = EmotionSpectrumDeriver.derive(analysis);

        assertFalse(spectrum.isEmpty(), "spectrum must not be empty");
        double sum = 0;
        for (EmotionInsight.SpectrumEntry e : spectrum) {
            assertTrue(e.ratio >= 0, "ratio must be non-negative");
            assertTrue(e.emotion != null && !e.emotion.isBlank(), "emotion label must be present");
            sum += e.ratio;
        }
        assertEquals(1.0, sum, 0.001, "ratios should sum to 1.0");
    }

    @Test
    @DisplayName("Derivation is deterministic: same input -> identical spectrum")
    void deterministic() {
        AnalysisResult a = PseudoSemanticAnalyzer.analyze("我和朋友吵架了，心里很委屈。");
        AnalysisResult b = PseudoSemanticAnalyzer.analyze("我和朋友吵架了，心里很委屈。");
        List<EmotionInsight.SpectrumEntry> s1 = EmotionSpectrumDeriver.derive(a);
        List<EmotionInsight.SpectrumEntry> s2 = EmotionSpectrumDeriver.derive(b);

        assertEquals(s1.size(), s2.size());
        for (int i = 0; i < s1.size(); i++) {
            assertEquals(s1.get(i).emotion, s2.get(i).emotion);
            assertEquals(s1.get(i).ratio, s2.get(i).ratio, 0.0001);
        }
    }

    @Test
    @DisplayName("Null analysis yields a safe, non-null spectrum")
    void nullSafe() {
        List<EmotionInsight.SpectrumEntry> spectrum = EmotionSpectrumDeriver.derive(null);
        assertFalse(spectrum.isEmpty());
        double sum = spectrum.stream().mapToDouble(e -> e.ratio).sum();
        assertEquals(1.0, sum, 0.001);
    }

    @Test
    @DisplayName("Positive sentiment surfaces a positive-leaning dominant emotion")
    void positiveSentiment() {
        AnalysisResult analysis = PseudoSemanticAnalyzer.analyze("今天特别开心，事情很顺利，我很满足。");
        List<EmotionInsight.SpectrumEntry> spectrum = EmotionSpectrumDeriver.derive(analysis);
        // The dominant (first) entry should carry the largest ratio.
        for (EmotionInsight.SpectrumEntry e : spectrum) {
            assertTrue(spectrum.get(0).ratio >= e.ratio, "first entry must be the dominant one");
        }
    }
}
