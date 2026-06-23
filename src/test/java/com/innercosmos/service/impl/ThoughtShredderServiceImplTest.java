package com.innercosmos.service.impl;

import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.entity.MemoryCard;
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

/** M-026: a DISPLAY_ONCE shred must be TRANSIENT + zero gravity (never resurface). */
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
    @DisplayName("M-026: DISPLAY_ONCE shred is TRANSIENT with zero gravity")
    void displayOnce_isTransientZeroGravity() {
        when(safetyService.check(anyString(), any(), any())).thenReturn(nonBlocking());
        StructuredAiResults.ShredderResult ai = new StructuredAiResults.ShredderResult();
        ai.coreFeeling = "累";
        ai.memoryType = "SHREDDER";
        when(structuredAiService.call(any(), anyString(), any(), any(), any(), any())).thenReturn(ai);

        service.process(1L, "今天太崩溃了撑不住了", "DISPLAY_ONCE");

        MemoryCard card = captureInsertedCard();
        assertEquals("TRANSIENT", card.status, "DISPLAY_ONCE must produce a TRANSIENT card");
        assertEquals(0.0, card.emotionalGravity, "TRANSIENT card must have zero gravity (no starfield)");
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
