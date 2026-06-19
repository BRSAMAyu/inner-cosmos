package com.innercosmos.service.impl;

import com.innercosmos.ai.semantic.EmotionInsight;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.entity.EmotionTrace;
import com.innercosmos.mapper.EmotionTraceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * IC-EMO-001 unit coverage for {@link EmotionInsightServiceImpl}:
 * - analyze() LLM primary path produces an "LLM" insight with a parsed spectrum
 * - analyze() falls back to a deterministic "LEXICON" insight when the LLM throws
 * - writeTrace() upserts by (user_id, source_session_id) and plain-inserts for diary
 * - fromSettlement() adapts an existing settlement result without a 2nd LLM call
 */
@ExtendWith(MockitoExtension.class)
class EmotionInsightServiceImplTest {

    @Mock
    private StructuredAiService structuredAiService;
    @Mock
    private EmotionTraceMapper emotionTraceMapper;

    private EmotionInsightServiceImpl service;

    private static final Long USER_ID = 7L;
    private static final Long SESSION_ID = 200L;

    @BeforeEach
    void setUp() {
        service = new EmotionInsightServiceImpl(structuredAiService, emotionTraceMapper);
    }

    @Test
    @DisplayName("analyze(): LLM path returns analysisSource=LLM with parsed spectrum")
    void analyze_llmPath() {
        StructuredAiResults.Emotion emotion = new StructuredAiResults.Emotion();
        emotion.emotionName = "焦虑";
        emotion.emotionScore = 8.0;
        emotion.weatherType = "FOGGY";
        emotion.triggerScene = "对话语义分析";
        emotion.spectrum = List.of(spectrum("焦虑", 0.6), spectrum("疲惫", 0.4));

        when(structuredAiService.call(eq(USER_ID), eq("EMOTION_INSIGHT"), any(), any(), any(), any()))
                .thenReturn(emotion);

        EmotionInsight insight = service.analyze(USER_ID, "今天作业太多我很焦虑");

        assertEquals("LLM", insight.analysisSource);
        assertEquals("焦虑", insight.primaryEmotion);
        assertEquals(8.0, insight.emotionScore, 0.0001);
        assertNotNull(insight.spectrum);
        assertFalse(insight.spectrum.isEmpty());
        assertEquals("焦虑", insight.spectrum.get(0).emotion);
    }

    @Test
    @DisplayName("analyze(): LLM throwing yields a deterministic LEXICON insight")
    void analyze_fallbackPath() {
        when(structuredAiService.call(eq(USER_ID), eq("EMOTION_INSIGHT"), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("LLM unavailable"));

        EmotionInsight insight = service.analyze(USER_ID, "今天作业堆得太多了，我很焦虑也很累。");

        assertEquals("LEXICON", insight.analysisSource);
        assertNotNull(insight.primaryEmotion);
        assertTrue(insight.emotionScore >= 0 && insight.emotionScore <= 10);
        assertNotNull(insight.weatherType);
        assertNotNull(insight.spectrum);
        assertFalse(insight.spectrum.isEmpty());
        // Never the old 6-keyword hardcoded trigger label.
        assertNotEquals("对话关键词提取", insight.triggerScene);
    }

    @Test
    @DisplayName("analyze(): emotion score is clamped into [0,10]")
    void analyze_clampsScore() {
        StructuredAiResults.Emotion emotion = new StructuredAiResults.Emotion();
        emotion.emotionName = "愤怒";
        emotion.emotionScore = 99.0;
        when(structuredAiService.call(eq(USER_ID), eq("EMOTION_INSIGHT"), any(), any(), any(), any()))
                .thenReturn(emotion);

        EmotionInsight insight = service.analyze(USER_ID, "气死了");
        assertEquals(10.0, insight.emotionScore, 0.0001);
    }

    @Test
    @DisplayName("writeTrace(): sessionId present + no prior row -> insert")
    void writeTrace_upsertInsertsWhenAbsent() {
        when(emotionTraceMapper.selectOne(any())).thenReturn(null);

        EmotionInsight insight = sampleInsight("LEXICON");
        service.writeTrace(USER_ID, SESSION_ID, insight);

        verify(emotionTraceMapper, times(1)).insert(any(EmotionTrace.class));
        verify(emotionTraceMapper, never()).updateById(any(EmotionTrace.class));
    }

    @Test
    @DisplayName("writeTrace(): sessionId present + prior row -> update (no duplicate)")
    void writeTrace_upsertUpdatesWhenPresent() {
        EmotionTrace prior = new EmotionTrace();
        prior.id = 555L;
        prior.userId = USER_ID;
        prior.sourceSessionId = SESSION_ID;
        when(emotionTraceMapper.selectOne(any())).thenReturn(prior);

        EmotionInsight insight = sampleInsight("SETTLEMENT");
        service.writeTrace(USER_ID, SESSION_ID, insight);

        ArgumentCaptor<EmotionTrace> captor = ArgumentCaptor.forClass(EmotionTrace.class);
        verify(emotionTraceMapper, times(1)).updateById(captor.capture());
        verify(emotionTraceMapper, never()).insert(any(EmotionTrace.class));
        assertEquals(555L, captor.getValue().id, "update must target the existing row id");
        assertEquals("SETTLEMENT", captor.getValue().analysisSource);
        assertNotNull(captor.getValue().emotionSpectrum);
    }

    @Test
    @DisplayName("writeTrace(): sessionId null (diary) -> plain insert, no query")
    void writeTrace_diaryInserts() {
        EmotionInsight insight = sampleInsight("SETTLEMENT");
        service.writeTrace(USER_ID, null, insight);

        verify(emotionTraceMapper, times(1)).insert(any(EmotionTrace.class));
        verify(emotionTraceMapper, never()).selectOne(any());
        verify(emotionTraceMapper, never()).updateById(any(EmotionTrace.class));
    }

    @Test
    @DisplayName("writeTrace(): DB failure is swallowed (never throws out)")
    void writeTrace_swallowsDbError() {
        when(emotionTraceMapper.selectOne(any())).thenThrow(new RuntimeException("db down"));
        assertDoesNotThrow(() -> service.writeTrace(USER_ID, SESSION_ID, sampleInsight("LEXICON")));
    }

    @Test
    @DisplayName("fromSettlement(): adapts settlement emotion with source=SETTLEMENT")
    void fromSettlement_adapts() {
        StructuredAiResults.SettlementResult result = new StructuredAiResults.SettlementResult();
        result.emotionTrace.emotionName = "难过";
        result.emotionTrace.emotionScore = 6.0;
        result.emotionTrace.weatherType = "RAINY";
        result.emotionTrace.triggerScene = "和朋友的一次冲突";

        EmotionInsight insight = service.fromSettlement(result);

        assertEquals("SETTLEMENT", insight.analysisSource);
        assertEquals("难过", insight.primaryEmotion);
        assertEquals(6.0, insight.emotionScore, 0.0001);
        assertNotNull(insight.spectrum);
        assertFalse(insight.spectrum.isEmpty());
        assertNotNull(insight.weatherType);
    }

    private StructuredAiResults.SpectrumEntry spectrum(String emotion, double ratio) {
        StructuredAiResults.SpectrumEntry e = new StructuredAiResults.SpectrumEntry();
        e.emotion = emotion;
        e.ratio = ratio;
        return e;
    }

    private EmotionInsight sampleInsight(String source) {
        EmotionInsight insight = new EmotionInsight();
        insight.primaryEmotion = "焦虑";
        insight.emotionScore = 7.0;
        insight.weatherType = "FOGGY";
        insight.triggerScene = "测试场景";
        insight.analysisSource = source;
        insight.spectrum = List.of(
                new EmotionInsight.SpectrumEntry("焦虑", 0.7),
                new EmotionInsight.SpectrumEntry("疲惫", 0.3));
        return insight;
    }
}
