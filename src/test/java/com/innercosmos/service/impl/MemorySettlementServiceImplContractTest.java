package com.innercosmos.service.impl;

import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.DialogSession;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.mapper.*;
import com.innercosmos.service.EmotionInsightService;
import com.innercosmos.service.GravityService;
import com.innercosmos.service.ThemeAggregationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemorySettlementServiceImplContractTest {
    @Mock MemoryCardMapper memoryCardMapper;
    @Mock ThoughtFragmentMapper thoughtFragmentMapper;
    @Mock EmotionTraceMapper emotionTraceMapper;
    @Mock TodoItemMapper todoItemMapper;
    @Mock EventCardMapper eventCardMapper;
    @Mock RelationMentionMapper relationMentionMapper;
    @Mock MemoryThemeMapper memoryThemeMapper;
    @Mock DailyRecordMapper dailyRecordMapper;
    @Mock DialogMessageMapper dialogMessageMapper;
    @Mock DialogSessionMapper dialogSessionMapper;
    @Mock VoiceTranscriptionMapper voiceTranscriptionMapper;
    @Mock GravityService gravityService;
    @Mock ThemeAggregationService themeAggregationService;
    @Mock StructuredAiService structuredAiService;
    @Mock EmotionInsightService emotionInsightService;

    @InjectMocks MemorySettlementServiceImpl service;

    @Test
    void conversationSettlementUsesChineseFallbackAndLifecycleProvenance() {
        DialogSession session = new DialogSession();
        session.userId = 7L;
        DialogMessage message = new DialogMessage();
        message.textContent = "今天的任务让我有点焦虑";
        when(dialogSessionMapper.selectById(19L)).thenReturn(session);
        when(dialogMessageMapper.selectList(any())).thenReturn(List.of(message));
        StructuredAiResults.SettlementResult ai = new StructuredAiResults.SettlementResult();
        when(structuredAiService.call(any(), anyString(), any(), any(), any(), any())).thenReturn(ai);
        when(gravityService.calculateGravity(anyDouble(), anyInt(), anyDouble(), anyInt(), anyLong()))
                .thenReturn(1.8);

        service.settleSession(7L, 19L);

        ArgumentCaptor<MemoryCard> captor = ArgumentCaptor.forClass(MemoryCard.class);
        verify(memoryCardMapper).insert(captor.capture());
        MemoryCard card = captor.getValue();
        assertEquals("今日沉淀", card.title);
        assertEquals(1, card.versionNo);
        assertEquals("EPISODIC", card.memoryLayer);
        assertEquals(0.85, card.confidence);
        assertEquals("AURORA_PRIVATE", card.consentScope);
        assertEquals("AURORA_SESSION:19 · source-version:1 · consent:AURORA_PRIVATE",
                card.provenanceRefs);
    }

    @Test
    void diaryIntensityIsClampedBeforeGravityCalculation() {
        StructuredAiResults.SettlementResult ai = new StructuredAiResults.SettlementResult();
        ai.memoryCard.intensityScore = 99.0;
        when(structuredAiService.call(any(), anyString(), any(), any(), any(), any())).thenReturn(ai);
        when(gravityService.calculateGravity(anyDouble(), anyInt(), anyDouble(), anyInt(), anyLong()))
                .thenReturn(2.0);

        service.settleDiary(7L, null, "今天写下一段日记");

        ArgumentCaptor<Double> intensity = ArgumentCaptor.forClass(Double.class);
        verify(gravityService).calculateGravity(intensity.capture(), eq(1), eq(4.0), eq(1), eq(0L));
        assertEquals(10.0, intensity.getValue());
        ArgumentCaptor<MemoryCard> card = ArgumentCaptor.forClass(MemoryCard.class);
        verify(memoryCardMapper).insert(card.capture());
        assertEquals(10.0, card.getValue().intensityScore);
    }
}
