package com.innercosmos.service.impl;

import com.innercosmos.ai.semantic.EmotionInsight;
import com.innercosmos.ai.semantic.MomentMood;
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
    @DisplayName("writeTrace(): concurrent-insert race -> on duplicate-key, re-query + updateById (no duplicate, no throw)")
    void writeTrace_raceFallbackRecoversViaUpdate() {
        // First lookup sees no row (both listeners observed null concurrently).
        // The insert then loses the race and the DB rejects it with a dup-key error.
        // A re-query now finds the winner's row, so we must recover via updateById.
        EmotionTrace winner = new EmotionTrace();
        winner.id = 909L;
        winner.userId = USER_ID;
        winner.sourceSessionId = SESSION_ID;
        when(emotionTraceMapper.selectOne(any()))
                .thenReturn(null)      // initial upsert lookup
                .thenReturn(winner);   // re-query after duplicate-key failure
        when(emotionTraceMapper.insert(any(EmotionTrace.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("Unique index violation"));

        EmotionInsight insight = sampleInsight("LEXICON");
        assertDoesNotThrow(() -> service.writeTrace(USER_ID, SESSION_ID, insight));

        ArgumentCaptor<EmotionTrace> captor = ArgumentCaptor.forClass(EmotionTrace.class);
        verify(emotionTraceMapper, times(1)).insert(any(EmotionTrace.class));
        verify(emotionTraceMapper, times(1)).updateById(captor.capture());
        assertEquals(909L, captor.getValue().id, "recovery update must target the winner row id");
        assertEquals("LEXICON", captor.getValue().analysisSource);
        assertNotNull(captor.getValue().emotionSpectrum);
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

    // ── IC-EMO-002: latestMood() ──

    @Test
    @DisplayName("latestMood(): no trace -> well-formed absent read (never null)")
    void latestMood_noTrace() {
        when(emotionTraceMapper.selectOne(any())).thenReturn(null);

        MomentMood mood = service.latestMood(USER_ID);

        assertNotNull(mood);
        assertFalse(mood.present);
        assertEquals(MomentMood.NEUTRAL_WEATHER, mood.weatherType);
        assertNotNull(mood.spectrum);
        assertTrue(mood.spectrum.isEmpty());
        assertNotNull(mood.momentLabel);
        assertFalse(mood.momentLabel.isBlank());
    }

    @Test
    @DisplayName("latestMood(): null userId -> absent read, no DB call")
    void latestMood_nullUser() {
        MomentMood mood = service.latestMood(null);

        assertNotNull(mood);
        assertFalse(mood.present);
        verify(emotionTraceMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("latestMood(): enriched trace -> primary emotion + intensity + top spectrum")
    void latestMood_enrichedTrace() {
        EmotionTrace trace = new EmotionTrace();
        trace.id = 11L;
        trace.userId = USER_ID;
        trace.emotionName = "平静";
        trace.emotionScore = 4.0;
        trace.weatherType = "SUNNY";
        trace.emotionSpectrum = "[{\"emotion\":\"平静\",\"ratio\":0.6},"
                + "{\"emotion\":\"期待\",\"ratio\":0.3},"
                + "{\"emotion\":\"疲惫\",\"ratio\":0.1}]";
        when(emotionTraceMapper.selectOne(any())).thenReturn(trace);

        MomentMood mood = service.latestMood(USER_ID);

        assertTrue(mood.present);
        assertEquals("平静", mood.primaryEmotion);
        assertEquals(4.0, mood.intensity, 0.0001);
        assertEquals("SUNNY", mood.weatherType);
        assertFalse(mood.spectrum.isEmpty());
        assertEquals("平静", mood.spectrum.get(0).emotion, "spectrum is sorted by ratio desc");
        assertTrue(mood.momentLabel.contains("平静"));
        assertTrue(mood.momentLabel.contains("60%"), "brief spectrum percent surfaced");
    }

    @Test
    @DisplayName("latestMood(): malformed spectrum JSON -> emotion-only label, no throw")
    void latestMood_malformedSpectrum() {
        EmotionTrace trace = new EmotionTrace();
        trace.id = 12L;
        trace.userId = USER_ID;
        trace.emotionName = "焦虑";
        trace.emotionScore = 6.0;
        trace.weatherType = "FOGGY";
        trace.emotionSpectrum = "not-json{{{";
        when(emotionTraceMapper.selectOne(any())).thenReturn(trace);

        MomentMood mood = service.latestMood(USER_ID);

        assertTrue(mood.present);
        assertEquals("焦虑", mood.primaryEmotion);
        assertTrue(mood.spectrum.isEmpty(), "malformed spectrum degrades to empty");
        assertEquals("焦虑", mood.momentLabel, "label falls back to emotion only");
    }

    @Test
    @DisplayName("latestMood(): null/blank spectrum (old Phase-1 row) -> emotion-only, no throw")
    void latestMood_nullSpectrum() {
        EmotionTrace trace = new EmotionTrace();
        trace.id = 13L;
        trace.userId = USER_ID;
        trace.emotionName = "喜悦";
        trace.emotionScore = 5.0;
        trace.weatherType = "SUNNY";
        trace.emotionSpectrum = null;
        when(emotionTraceMapper.selectOne(any())).thenReturn(trace);

        MomentMood mood = service.latestMood(USER_ID);

        assertTrue(mood.present);
        assertEquals("喜悦", mood.primaryEmotion);
        assertTrue(mood.spectrum.isEmpty());
        assertEquals("喜悦", mood.momentLabel);
    }

    @Test
    @DisplayName("latestMood(): DB error -> absent read (swallowed, never throws)")
    void latestMood_dbErrorSwallowed() {
        when(emotionTraceMapper.selectOne(any())).thenThrow(new RuntimeException("db down"));

        MomentMood mood = service.latestMood(USER_ID);

        assertNotNull(mood);
        assertFalse(mood.present);
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
