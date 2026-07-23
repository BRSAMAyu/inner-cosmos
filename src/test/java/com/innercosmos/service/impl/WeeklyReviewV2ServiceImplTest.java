package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.DailyRecord;
import com.innercosmos.entity.EmotionTimeline;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.TodoItem;
import com.innercosmos.entity.WeeklyReview;
import com.innercosmos.mapper.DailyRecordMapper;
import com.innercosmos.mapper.EmotionTimelineMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.TodoItemMapper;
import com.innercosmos.mapper.WeeklyReviewMapper;
import com.innercosmos.service.EmotionPatternService;
import com.innercosmos.service.WeeklyReviewV2Service;
import com.innercosmos.vo.WeeklyReviewV2VO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * G2.ARCH-MODULES: DailyRecordController now depends on the new WeeklyReviewV2Service interface
 * instead of the concrete service.impl class directly. This is the first test coverage this
 * (previously untested) service has had; it pins the interface's observable contract through the
 * rename/extract-interface refactor.
 */
class WeeklyReviewV2ServiceImplTest {

    private final WeeklyReviewMapper weeklyReviewMapper = mock(WeeklyReviewMapper.class);
    private final DailyRecordMapper dailyRecordMapper = mock(DailyRecordMapper.class);
    private final MemoryCardMapper memoryCardMapper = mock(MemoryCardMapper.class);
    private final TodoItemMapper todoItemMapper = mock(TodoItemMapper.class);
    private final EmotionTimelineMapper emotionTimelineMapper = mock(EmotionTimelineMapper.class);
    private final EmotionPatternService emotionPatternService = mock(EmotionPatternService.class);

    private final WeeklyReviewV2Service service = new WeeklyReviewV2ServiceImpl(
            weeklyReviewMapper, dailyRecordMapper, memoryCardMapper, todoItemMapper,
            emotionTimelineMapper, emotionPatternService);

    @Test
    void latestReturnsNullWhenNoReviewRowExistsYet() {
        when(weeklyReviewMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        assertNull(service.latest(7L));
    }

    @Test
    void latestReturnsALegacyVoWhenTheExistingRowAlreadyHasV2FieldsPopulated() {
        WeeklyReview existing = new WeeklyReview();
        existing.id = 5L;
        existing.userId = 7L;
        existing.weekStartDate = LocalDate.of(2026, 7, 20);
        existing.weekEndDate = LocalDate.of(2026, 7, 26);
        existing.dominantTheme = "平静";
        existing.auroraObservation = "本周你很平静";
        when(weeklyReviewMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);

        WeeklyReviewV2VO vo = service.latest(7L);

        assertEquals(5L, vo.id);
        assertEquals(7L, vo.userId);
        assertEquals("平静", vo.title);
        assertEquals(Boolean.TRUE, vo.legacy);
        assertEquals("2026-07-20 ~ 2026-07-26", vo.dateRange);
    }

    @Test
    void generateForRangeProducesADailySnapshotForEveryDayInTheRangeAndDefaultRecommendationWhenNoPattern() {
        when(dailyRecordMapper.selectList(any(QueryWrapper.class))).thenReturn(List.<DailyRecord>of());
        when(emotionTimelineMapper.selectList(any(QueryWrapper.class))).thenReturn(List.<EmotionTimeline>of());
        when(memoryCardMapper.selectList(any(QueryWrapper.class))).thenReturn(List.<MemoryCard>of());
        when(todoItemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.<TodoItem>of());
        when(emotionPatternService.getDominantPattern(7L, 30)).thenReturn(null);

        LocalDate start = LocalDate.of(2026, 7, 20);
        LocalDate end = start.plusDays(6);
        WeeklyReviewV2VO vo = service.generateForRange(7L, start, end);

        assertEquals(7L, vo.userId);
        assertEquals(7, vo.dailySnapshots.size());
        assertEquals("2026-07-20 ~ 2026-07-26", vo.dateRange);
        assertEquals("平静", vo.dominantEmotion);
        assertEquals(Boolean.FALSE, vo.legacy);
        assertTrue(vo.recommendation.contains("平静"));
    }

    @Test
    void saveInsertsANewRowWhenNoneExistsForThatWeek() {
        when(weeklyReviewMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
        WeeklyReviewV2VO vo = new WeeklyReviewV2VO();
        vo.userId = 7L;
        vo.weekStartDate = "2026-07-20";
        vo.weekEndDate = "2026-07-26";
        vo.dominantEmotion = "平静";

        service.save(vo);

        verify(weeklyReviewMapper).insert(any(WeeklyReview.class));
        verify(weeklyReviewMapper, never()).updateById(any(WeeklyReview.class));
    }

    @Test
    void saveUpdatesTheExistingRowForThatWeekInstead() {
        WeeklyReview existing = new WeeklyReview();
        existing.id = 9L;
        when(weeklyReviewMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);
        WeeklyReviewV2VO vo = new WeeklyReviewV2VO();
        vo.userId = 7L;
        vo.weekStartDate = "2026-07-20";
        vo.weekEndDate = "2026-07-26";
        vo.dominantEmotion = "平静";

        WeeklyReviewV2VO saved = service.save(vo);

        assertEquals(9L, saved.id);
        verify(weeklyReviewMapper).updateById(existing);
        verify(weeklyReviewMapper, never()).insert(any(WeeklyReview.class));
    }
}
