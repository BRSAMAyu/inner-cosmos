package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.entity.DailyRecord;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.MemoryTheme;
import com.innercosmos.entity.TodoItem;
import com.innercosmos.entity.WeeklyReview;
import com.innercosmos.mapper.DailyRecordMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.MemoryThemeMapper;
import com.innercosmos.mapper.TodoItemMapper;
import com.innercosmos.mapper.WeeklyReviewMapper;
import com.innercosmos.service.WeeklyReviewService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WeeklyReviewServiceImpl implements WeeklyReviewService {

    private final WeeklyReviewMapper weeklyReviewMapper;
    private final DailyRecordMapper dailyRecordMapper;
    private final MemoryCardMapper memoryCardMapper;
    private final TodoItemMapper todoItemMapper;
    private final MemoryThemeMapper memoryThemeMapper;
    private final StructuredAiService structuredAiService;

    public WeeklyReviewServiceImpl(WeeklyReviewMapper weeklyReviewMapper,
                                   DailyRecordMapper dailyRecordMapper,
                                   MemoryCardMapper memoryCardMapper,
                                   TodoItemMapper todoItemMapper,
                                   MemoryThemeMapper memoryThemeMapper,
                                   StructuredAiService structuredAiService) {
        this.weeklyReviewMapper = weeklyReviewMapper;
        this.dailyRecordMapper = dailyRecordMapper;
        this.memoryCardMapper = memoryCardMapper;
        this.todoItemMapper = todoItemMapper;
        this.memoryThemeMapper = memoryThemeMapper;
        this.structuredAiService = structuredAiService;
    }

    @Override
    @Transactional
    public WeeklyReview generateWeeklyReview(Long userId) {
        LocalDate weekStartDate = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate weekEndDate = weekStartDate.plusDays(6);

        // Query daily records for this week
        QueryWrapper<DailyRecord> recordQuery = new QueryWrapper<>();
        recordQuery.eq("user_id", userId).ge("record_date", weekStartDate);
        List<DailyRecord> records = dailyRecordMapper.selectList(recordQuery);

        // Count completed todos
        Long completedCount = todoItemMapper.selectCount(
                new QueryWrapper<TodoItem>().eq("user_id", userId).eq("status", "DONE"));
        Long totalCount = todoItemMapper.selectCount(
                new QueryWrapper<TodoItem>().eq("user_id", userId));

        // Get top 3 memory cards by emotional gravity
        List<MemoryCard> topMemories = memoryCardMapper.selectList(
                new QueryWrapper<MemoryCard>()
                        .eq("user_id", userId)
                        .orderByDesc("emotional_gravity")
                        .last("LIMIT 3"));

        // Get active memory themes
        List<MemoryTheme> activeThemes = memoryThemeMapper.selectList(
                new QueryWrapper<MemoryTheme>()
                        .eq("user_id", userId)
                        .eq("status", "ACTIVE")
                        .orderByDesc("average_gravity"));

        // Build summaries
        String themeSummary = activeThemes.stream()
                .map(t -> t.themeName)
                .collect(Collectors.joining(", "));
        if (themeSummary.isEmpty()) {
            themeSummary = "本周暂无活跃主题";
        }

        String dominantTheme = activeThemes.isEmpty() ? null : activeThemes.get(0).themeName;

        // Build emotion trend from daily records
        String emotionTrend = records.stream()
                .map(r -> r.emotionWeather)
                .filter(e -> e != null && !e.isBlank())
                .collect(Collectors.joining(" -> "));
        if (emotionTrend.isEmpty()) {
            emotionTrend = "暂无情绪记录";
        }

        // Build gravity change summary from top memories
        String gravitySummary = topMemories.stream()
                .map(m -> m.title + "(引力:" + (m.emotionalGravity != null ? String.format("%.1f", m.emotionalGravity) : "N/A") + ")")
                .collect(Collectors.joining("; "));
        if (gravitySummary.isEmpty()) {
            gravitySummary = "暂无高引力记忆";
        }

        // Build aurora observation
        String auroraObservation = records.stream()
                .map(r -> r.auroraSummary)
                .filter(a -> a != null && !a.isBlank())
                .collect(Collectors.joining(" | "));
        if (auroraObservation.isEmpty()) {
            auroraObservation = "本周暂无极光观察记录";
        }
        final String fallbackDominantTheme = dominantTheme;
        final String fallbackThemeSummary = themeSummary;
        final String fallbackEmotionTrend = emotionTrend;
        final String fallbackGravitySummary = gravitySummary;
        final String fallbackAuroraObservation = auroraObservation;
        StructuredAiResults.WeeklyResult ai = structuredAiService.call(userId, "WEEKLY_REVIEW",
                """
                Return JSON for a weekly review with dominantTheme, themeSummary,
                emotionTrend, gravityChangeSummary, weeklyObservation.
                Use the supplied records only. Be specific, warm, non-clinical, and action-light.
                """,
                java.util.Map.of(
                        "weekStartDate", weekStartDate,
                        "weekEndDate", weekEndDate,
                        "dailyRecords", records,
                        "topMemories", topMemories,
                        "activeThemes", activeThemes,
                        "completedTodos", completedCount,
                        "totalTodos", totalCount),
                StructuredAiResults.WeeklyResult.class,
                () -> fallbackWeekly(fallbackDominantTheme, fallbackThemeSummary, fallbackEmotionTrend,
                        fallbackGravitySummary, fallbackAuroraObservation));

        // Check if review already exists for this week
        WeeklyReview existing = weeklyReviewMapper.selectOne(
                new QueryWrapper<WeeklyReview>()
                        .eq("user_id", userId)
                        .eq("week_start_date", weekStartDate));

        WeeklyReview review = existing != null ? existing : new WeeklyReview();
        review.userId = userId;
        review.weekStartDate = weekStartDate;
        review.weekEndDate = weekEndDate;
        review.dominantTheme = blank(ai.dominantTheme, dominantTheme);
        review.themeSummary = blank(ai.themeSummary, themeSummary);
        review.emotionTrend = blank(ai.emotionTrend, emotionTrend);
        review.completedTodos = completedCount.intValue();
        review.totalTodos = totalCount.intValue();
        review.gravityChangeSummary = blank(ai.gravityChangeSummary, gravitySummary);
        review.auroraObservation = blank(ai.weeklyObservation, auroraObservation);
        review.status = "GENERATED";

        if (existing != null) {
            weeklyReviewMapper.updateById(review);
        } else {
            weeklyReviewMapper.insert(review);
        }

        return review;
    }

    @Override
    public WeeklyReview latest(Long userId) {
        return weeklyReviewMapper.selectOne(
                new QueryWrapper<WeeklyReview>()
                        .eq("user_id", userId)
                        .orderByDesc("week_start_date")
                        .last("LIMIT 1"));
    }

    private StructuredAiResults.WeeklyResult fallbackWeekly(String dominantTheme, String themeSummary,
                                                            String emotionTrend, String gravitySummary,
                                                            String auroraObservation) {
        StructuredAiResults.WeeklyResult result = new StructuredAiResults.WeeklyResult();
        result.dominantTheme = dominantTheme;
        result.themeSummary = themeSummary;
        result.emotionTrend = emotionTrend;
        result.gravityChangeSummary = gravitySummary;
        result.weeklyObservation = auroraObservation;
        return result;
    }

    private String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
