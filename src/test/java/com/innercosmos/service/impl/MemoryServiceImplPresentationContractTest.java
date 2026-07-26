package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.agent.MemoryExtractAgent;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.mapper.*;
import com.innercosmos.service.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MemoryServiceImplPresentationContractTest {
    @Test
    void latestDailyRecordFiltersTransientAndShredderMemories() {
        MemoryCardMapper cards = mock(MemoryCardMapper.class);
        MemoryServiceImpl service = service(cards);
        when(cards.selectOne(any())).thenReturn(null);

        service.latestDailyRecord(7L);

        @SuppressWarnings("unchecked")
        var query = org.mockito.ArgumentCaptor.forClass(QueryWrapper.class);
        verify(cards).selectOne(query.capture());
        String sql = query.getValue().getSqlSegment();
        assertTrue(sql.contains("status"), sql);
        assertTrue(sql.contains("memory_type"), sql);
        assertTrue(sql.contains("<>"), sql);
    }

    /**
     * The record card's relationship cues must be scoped to that day's own memory card. Reusing the
     * user's all-time mentions would present last week's cues as if they happened today.
     */
    @Test
    void latestDailyRecordScopesRelationCuesToTheDaysMemoryCard() {
        MemoryCardMapper cards = mock(MemoryCardMapper.class);
        RelationMentionMapper relations = mock(RelationMentionMapper.class);
        MemoryCard card = new MemoryCard();
        card.id = 42L;
        card.title = "今日沉淀";
        card.summary = "摘要";
        when(cards.selectOne(any())).thenReturn(card);
        when(relations.selectList(any())).thenReturn(List.of());

        service(cards, relations).latestDailyRecord(7L);

        @SuppressWarnings("unchecked")
        var query = org.mockito.ArgumentCaptor.forClass(QueryWrapper.class);
        verify(relations).selectList(query.capture());
        String sql = query.getValue().getSqlSegment();
        assertTrue(sql.contains("memory_card_id"), sql);
        assertTrue(sql.contains("user_id"), sql);
    }

    @Test
    void todoStarThemeIsChinese() {
        MemoryCardMapper cards = mock(MemoryCardMapper.class);
        MemoryCard card = new MemoryCard();
        card.id = 1L;
        card.title = "今日沉淀";
        card.memoryType = "TODO";
        card.status = "ACTIVE";
        card.emotionalGravity = 1.0;
        card.confidence = 0.85;
        when(cards.selectList(any())).thenReturn(List.of(card));

        var stars = service(cards).starfield(7L);

        assertEquals("需要温柔推进的下一步", stars.getFirst().theme);
    }

    private MemoryServiceImpl service(MemoryCardMapper cards) {
        return service(cards, mock(RelationMentionMapper.class));
    }

    private MemoryServiceImpl service(MemoryCardMapper cards, RelationMentionMapper relations) {
        return new MemoryServiceImpl(
                cards,
                mock(DialogMessageMapper.class),
                mock(ThoughtFragmentMapper.class),
                mock(EmotionTraceMapper.class),
                mock(TodoItemMapper.class),
                mock(GravityService.class),
                mock(MemoryExtractAgent.class),
                relations,
                mock(ThemeAggregationService.class),
                mock(DailyRecordMapper.class),
                mock(EmotionInsightService.class),
                mock(DialogService.class));
    }
}
