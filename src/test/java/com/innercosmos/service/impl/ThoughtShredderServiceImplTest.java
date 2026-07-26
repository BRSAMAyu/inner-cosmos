package com.innercosmos.service.impl;

import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.ThoughtFragment;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.ThoughtFragmentMapper;
import com.innercosmos.mapper.TodoItemMapper;
import com.innercosmos.service.GravityService;
import com.innercosmos.service.SafetyService;
import com.innercosmos.vo.SafetyResult;
import com.innercosmos.vo.ShredderResultVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Retention-mode contracts for the private thought shredder. */
@ExtendWith(MockitoExtension.class)
class ThoughtShredderServiceImplTest {

    @Mock MemoryCardMapper memoryCardMapper;
    @Mock ThoughtFragmentMapper thoughtFragmentMapper;
    @Mock TodoItemMapper todoItemMapper;
    @Mock GravityService gravityService;
    @Mock SafetyService safetyService;
    @Mock StructuredAiService structuredAiService;

    @InjectMocks ThoughtShredderServiceImpl service;

    @Test
    @DisplayName("DISPLAY_ONCE returns a transient result without persisting card, fragments, or todo")
    void displayOnce_doesNotPersistStructuredResults() {
        when(safetyService.check(anyString(), any(), any())).thenReturn(nonBlocking());
        StructuredAiResults.ShredderResult ai = new StructuredAiResults.ShredderResult();
        ai.coreFeeling = "累";
        ai.memoryType = "SHREDDER";
        when(structuredAiService.call(any(), anyString(), any(), any(), any(), any())).thenReturn(ai);

        ShredderResultVO result = service.process(1L, "今天太崩溃了撑不住了，明天还有任务", "DISPLAY_ONCE");

        assertEquals("TRANSIENT", result.memoryCard.status);
        assertEquals(0.0, result.memoryCard.emotionalGravity);
        assertNull(result.memoryCard.id);
        assertFalse(result.fragments.isEmpty(), "one-time response still contains useful fragments");
        verify(memoryCardMapper, never()).insert(any(MemoryCard.class));
        verify(thoughtFragmentMapper, never()).insert(any(ThoughtFragment.class));
        verify(todoItemMapper, never()).insert(any(com.innercosmos.entity.TodoItem.class));
        verify(gravityService, never()).calculateGravity(anyDouble(), anyInt(), anyDouble(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("M-026: KEEP_RAW shred is ACTIVE with real gravity")
    void keepRaw_isActiveWithGravity() {
        when(safetyService.check(anyString(), any(), any())).thenReturn(nonBlocking());
        when(gravityService.calculateGravity(anyDouble(), anyInt(), anyDouble(), anyInt(), anyLong()))
                .thenReturn(2.5);
        StructuredAiResults.ShredderResult ai = new StructuredAiResults.ShredderResult();
        ai.coreFeeling = "累";
        ai.memoryType = "SHREDDER";
        when(structuredAiService.call(any(), anyString(), any(), any(), any(), any())).thenReturn(ai);

        service.process(1L, "今天太崩溃了", "KEEP_RAW");

        MemoryCard card = captureInsertedCard();
        assertEquals("ACTIVE", card.status);
        assertEquals(2.5, card.emotionalGravity);
        assertEquals(0.78, card.confidence);
        assertEquals("EPISODIC", card.memoryLayer);
        assertTrue(card.provenanceRefs.contains("THOUGHT_SHREDDER:KEEP_RAW"));
    }

    @Test
    @DisplayName("KEEP_RAW retains source excerpts while KEEP_ONLY_RESULT persists derived excerpts")
    void retentionModesHaveDifferentPersistedExcerpts() {
        when(safetyService.check(anyString(), any(), any())).thenReturn(nonBlocking());
        when(gravityService.calculateGravity(anyDouble(), anyInt(), anyDouble(), anyInt(), anyLong()))
                .thenReturn(1.5);
        StructuredAiResults.ShredderResult ai = new StructuredAiResults.ShredderResult();
        ai.coreFeeling = "焦虑";
        ai.hiddenNeed = "把压力拆小";
        ai.memoryType = "SHREDDER";
        StructuredAiResults.Fragment fragment = new StructuredAiResults.Fragment();
        fragment.type = "FEELING";
        fragment.rawExcerpt = "这是我不想长期保存的原话";
        fragment.analysis = "识别到焦虑";
        fragment.reframe = "先停一下";
        ai.fragments.add(fragment);
        when(structuredAiService.call(any(), anyString(), any(), any(), any(), any())).thenReturn(ai);

        service.process(1L, "这是我不想长期保存的原话", "KEEP_ONLY_RESULT");
        service.process(1L, "这是我愿意保存的原话", "KEEP_RAW");

        ArgumentCaptor<ThoughtFragment> fragments = ArgumentCaptor.forClass(ThoughtFragment.class);
        verify(thoughtFragmentMapper, times(8)).insert(fragments.capture());
        assertEquals("焦虑", fragments.getAllValues().get(0).rawExcerpt);
        assertEquals("这是我不想长期保存的原话", fragments.getAllValues().get(4).rawExcerpt);
    }

    private SafetyResult nonBlocking() {
        SafetyResult r = new SafetyResult();
        r.blockModelCall = false;
        r.riskLevel = "LOW";
        return r;
    }

    private MemoryCard captureInsertedCard() {
        ArgumentCaptor<MemoryCard> captor = ArgumentCaptor.forClass(MemoryCard.class);
        verify(memoryCardMapper).insert(captor.capture());
        return captor.getValue();
    }
}
