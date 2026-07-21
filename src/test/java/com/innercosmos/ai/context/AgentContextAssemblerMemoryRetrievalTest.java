package com.innercosmos.ai.context;

import com.innercosmos.ai.perception.GeocodingService;
import com.innercosmos.ai.perception.TimeContextService;
import com.innercosmos.ai.perception.WeatherContextService;
import com.innercosmos.dto.MemoryRetrievalQuery;
import com.innercosmos.entity.UserProfile;
import com.innercosmos.mapper.DailyRecordMapper;
import com.innercosmos.mapper.DialogMessageMapper;
import com.innercosmos.mapper.EmotionTraceMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.MemoryThemeMapper;
import com.innercosmos.mapper.RelationMentionMapper;
import com.innercosmos.mapper.TodoItemMapper;
import com.innercosmos.mapper.UserProfileMapper;
import com.innercosmos.mapper.WeeklyReviewMapper;
import com.innercosmos.service.MemoryRetrievalService;
import com.innercosmos.vo.MemoryEvidencePackVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentContextAssemblerMemoryRetrievalTest {
    @Mock private UserProfileMapper userProfileMapper;
    @Mock private DialogMessageMapper dialogMessageMapper;
    @Mock private MemoryCardMapper memoryCardMapper;
    @Mock private TodoItemMapper todoItemMapper;
    @Mock private DailyRecordMapper dailyRecordMapper;
    @Mock private WeeklyReviewMapper weeklyReviewMapper;
    @Mock private EmotionTraceMapper emotionTraceMapper;
    @Mock private RelationMentionMapper relationMentionMapper;
    @Mock private MemoryThemeMapper memoryThemeMapper;
    @Mock private GeocodingService geocodingService;
    @Mock private WeatherContextService weatherContextService;
    @Mock private TimeContextService timeContextService;
    @Mock private MemoryRetrievalService memoryRetrievalService;

    private AgentContextAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new AgentContextAssembler(userProfileMapper, dialogMessageMapper, memoryCardMapper,
                todoItemMapper, dailyRecordMapper, weeklyReviewMapper, emotionTraceMapper,
                relationMentionMapper, memoryThemeMapper, geocodingService, weatherContextService,
                timeContextService);
        ReflectionTestUtils.setField(assembler, "memoryRetrievalService", memoryRetrievalService);

        lenient().when(timeContextService.now(anyBoolean(), any()))
                .thenReturn(new TimeContextService.TimeContext("下午", "2026-07-21", false, false, null));
        lenient().when(dialogMessageMapper.selectList(any())).thenReturn(List.of());
        lenient().when(todoItemMapper.selectList(any())).thenReturn(List.of());
        lenient().when(todoItemMapper.selectOne(any())).thenReturn(null);
        lenient().when(dailyRecordMapper.selectList(any())).thenReturn(List.of());
        lenient().when(weeklyReviewMapper.selectList(any())).thenReturn(List.of());
        lenient().when(relationMentionMapper.selectList(any())).thenReturn(List.of());
        lenient().when(memoryThemeMapper.selectList(any())).thenReturn(List.of());
        lenient().when(emotionTraceMapper.selectOne(any())).thenReturn(null);
    }

    @Test
    void usesTaskAwareEvidencePackInsteadOfGravityQuery() {
        when(userProfileMapper.selectOne(any())).thenReturn(null);
        var evidence = new MemoryEvidencePackVO.Evidence(91L, "展示准备", "上次先做两分钟演练后更安心",
                "PROCEDURAL", 0.87, List.of("适合当前任务 1.0"), 3, "dialog:501");
        when(memoryRetrievalService.retrieve(any(), any())).thenReturn(new MemoryEvidencePackVO(
                "AURORA_ACTION", "明天考试，我该做什么", 800, 32, List.of(evidence),
                List.of("FORGOTTEN", "SUPERSEDED", "ARCHIVED", "CONTRADICTED")));

        AgentContext context = assembler.assemble(7L, 11L, "明天考试，我该做什么", true);

        assertThat(context.evidenceMemoryIds).containsExactly(91L);
        assertThat(context.longTermMemories).containsExactly("#91 展示准备：上次先做两分钟演练后更安心");
        ArgumentCaptor<MemoryRetrievalQuery> query = ArgumentCaptor.forClass(MemoryRetrievalQuery.class);
        verify(memoryRetrievalService).retrieve(org.mockito.ArgumentMatchers.eq(7L), query.capture());
        assertThat(query.getValue().task()).isEqualTo("AURORA_ACTION");
        assertThat(query.getValue().maxResults()).isEqualTo(8);
        assertThat(query.getValue().tokenBudget()).isEqualTo(800);
        assertThat(query.getValue().includeContradicted()).isFalse();
        assertThat(query.getValue().allowedLayers()).isEmpty();
        verifyNoInteractions(memoryCardMapper);
    }

    @Test
    void memoryOptOutSkipsRetrievalAndReturnsNoEvidence() {
        UserProfile profile = new UserProfile();
        profile.allowMemoryRecall = false;
        when(userProfileMapper.selectOne(any())).thenReturn(profile);

        AgentContext context = assembler.assemble(7L, 11L, "聊聊以前", true);

        assertThat(context.memoryRecallAllowed).isFalse();
        assertThat(context.longTermMemories).isEmpty();
        assertThat(context.evidenceMemoryIds).isEmpty();
        verify(memoryRetrievalService, never()).retrieve(any(), any());
        verifyNoInteractions(memoryCardMapper);
    }

    @Test
    void retrievalFailureFailsClosedWithoutGravityFallback() {
        when(userProfileMapper.selectOne(any())).thenReturn(null);
        when(memoryRetrievalService.retrieve(any(), any())).thenThrow(new IllegalStateException("database unavailable"));

        AgentContext context = assembler.assemble(7L, 11L, "想起以前的事", true);

        assertThat(context.longTermMemories).isEmpty();
        assertThat(context.evidenceMemoryIds).isEmpty();
        verifyNoInteractions(memoryCardMapper);
    }
}
