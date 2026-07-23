package com.innercosmos.ai.semantic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PseudoSemanticAnalyzerTest {

    @Test
    void mixedPresentationEmotionKeepsDomainIntentAndBothThemes() {
        var result = PseudoSemanticAnalyzer.analyze(
                "我明天要做项目展示，既兴奋又焦虑，先陪我稳一下再找第一步");

        assertEquals("TASK_STRESS", result.primaryIntent);
        assertTrue(result.detectedThemes.contains("任务压力"));
        assertTrue(result.detectedThemes.contains("情绪承压"));
        assertEquals("ACTION_SPLIT", result.suggestedMode);
    }

    @Test
    void ordinaryPhraseDoesNotManufactureThemesFromSingleCharacters() {
        var result = PseudoSemanticAnalyzer.analyze("我今天在黄昏散步");

        assertEquals("DAILY_SHARE", result.primaryIntent);
        assertEquals(java.util.List.of("日常分享"), result.detectedThemes);
        assertFalse(result.needsSafetyIntervention);
    }

    @Test
    void crisisPhraseRemainsHighestPriorityAndSafetyRelevant() {
        var result = PseudoSemanticAnalyzer.analyze("项目做不完，我甚至不想活了");

        assertEquals("SELF_HARM", result.primaryIntent);
        assertTrue(result.needsSafetyIntervention);
    }

    /**
     * 2026-07-24 8-agent audit (P0-2): an ordinary, plausible first message combining two mild
     * negative words ("焦虑"=-3, "压力"=-2) additively summed past the old CRISIS threshold
     * (score &lt;= -4) with no real self-harm/severe signal present, producing an alarming
     * emergency-support reply for entirely mundane stress. CRISIS must require an actual
     * severe/self-harm signal, never a sum of merely-mild words.
     */
    @Test
    void ordinaryStressPhraseNeverClassifiesAsCrisis() {
        var result = PseudoSemanticAnalyzer.analyze("我今天有点焦虑，工作压力很大");

        assertFalse(result.needsSafetyIntervention);
        org.junit.jupiter.api.Assertions.assertNotEquals("CRISIS", result.sentimentLabel);
        org.junit.jupiter.api.Assertions.assertNotEquals("SELF_HARM", result.primaryIntent);
    }

    @Test
    void singleSevereWordAloneStillClassifiesAsCrisis() {
        var result = PseudoSemanticAnalyzer.analyze("我今天真的崩溃了");

        assertEquals("CRISIS", result.sentimentLabel);
    }
}
