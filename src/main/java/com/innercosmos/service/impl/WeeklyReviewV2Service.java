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
import com.innercosmos.vo.EmotionPatternVO;
import com.innercosmos.vo.WeeklyDailySnapshotVO;
import com.innercosmos.vo.WeeklyReviewV2VO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for generating WeeklyReviewV2 with real data aggregation.
 * Uses actual DailyRecord, MemoryCard, TodoItem, and EmotionTimeline data
 * to produce a richer weekly review than the legacy WeeklyReview entity.
 */
@Service
public class WeeklyReviewV2Service {

    private static final Logger log = LoggerFactory.getLogger(WeeklyReviewV2Service.class);

    private final WeeklyReviewMapper weeklyReviewMapper;
    private final DailyRecordMapper dailyRecordMapper;
    private final MemoryCardMapper memoryCardMapper;
    private final TodoItemMapper todoItemMapper;
    private final EmotionTimelineMapper emotionTimelineMapper;
    private final EmotionPatternService emotionPatternService;

    public WeeklyReviewV2Service(WeeklyReviewMapper weeklyReviewMapper,
                                 DailyRecordMapper dailyRecordMapper,
                                 MemoryCardMapper memoryCardMapper,
                                 TodoItemMapper todoItemMapper,
                                 EmotionTimelineMapper emotionTimelineMapper,
                                 EmotionPatternService emotionPatternService) {
        this.weeklyReviewMapper = weeklyReviewMapper;
        this.dailyRecordMapper = dailyRecordMapper;
        this.memoryCardMapper = memoryCardMapper;
        this.todoItemMapper = todoItemMapper;
        this.emotionTimelineMapper = emotionTimelineMapper;
        this.emotionPatternService = emotionPatternService;
    }

    /**
     * Generate a V2 weekly review for the current week.
     */
    @Transactional
    public WeeklyReviewV2VO generate(Long userId) {
        LocalDate weekStartDate = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate weekEndDate = weekStartDate.plusDays(6);

        return generateForRange(userId, weekStartDate, weekEndDate);
    }

    /**
     * Generate a V2 weekly review for a specific date range.
     */
    @Transactional
    public WeeklyReviewV2VO generateForRange(Long userId, LocalDate weekStartDate, LocalDate weekEndDate) {
        // Query daily records for the week
        QueryWrapper<DailyRecord> recordQuery = new QueryWrapper<>();
        recordQuery.eq("user_id", userId)
                .ge("record_date", weekStartDate)
                .le("record_date", weekEndDate)
                .orderByAsc("record_date");
        List<DailyRecord> records = dailyRecordMapper.selectList(recordQuery);

        // Query emotion timeline for the week
        QueryWrapper<EmotionTimeline> timelineQuery = new QueryWrapper<>();
        timelineQuery.eq("user_id", userId)
                .ge("record_date", weekStartDate)
                .le("record_date", weekEndDate)
                .orderByAsc("record_date");
        List<EmotionTimeline> timelines = emotionTimelineMapper.selectList(timelineQuery);

        // Query memory cards for the week
        LocalDateTime startOfWeek = weekStartDate.atStartOfDay();
        LocalDateTime endOfWeek = weekEndDate.plusDays(1).atStartOfDay();
        QueryWrapper<MemoryCard> memoryQuery = new QueryWrapper<>();
        memoryQuery.eq("user_id", userId)
                .eq("status", "ACTIVE")
                .ge("created_at", startOfWeek)
                .lt("created_at", endOfWeek);
        List<MemoryCard> weekMemories = memoryCardMapper.selectList(memoryQuery);

        // Query todos
        QueryWrapper<TodoItem> todoQuery = new QueryWrapper<>();
        todoQuery.eq("user_id", userId);
        List<TodoItem> allTodos = todoItemMapper.selectList(todoQuery);

        // Build daily snapshots
        List<WeeklyDailySnapshotVO> dailySnapshots = buildDailySnapshots(records, weekStartDate, weekEndDate);

        // Build top themes
        String topThemes = buildTopThemes(weekMemories);

        // Build dominant emotion
        String dominantEmotion = buildDominantEmotion(timelines);

        // Build emotion spectrum
        String emotionSpectrum = buildEmotionSpectrum(timelines);

        // Calculate intensity average
        double intensityAverage = timelines.stream()
                .filter(t -> t.intensityAverage != null)
                .mapToDouble(t -> t.intensityAverage)
                .average()
                .orElse(0.0);

        // Calculate todo ratio
        int weekNumber = weekStartDate.get(java.time.temporal.WeekFields.of(Locale.getDefault()).weekOfYear());
        String todoRatio = buildTodoRatio(allTodos, weekStartDate, weekEndDate);

        // Build recommendation based on pattern analysis
        EmotionPatternVO dominantPattern = emotionPatternService.getDominantPattern(userId, 30);
        String recommendation = buildRecommendation(dominantPattern, dominantEmotion, weekMemories.size());

        // Build aurora observation
        String auroraObservation = buildAuroraObservation(records, timelines);

        // Build date range string
        String dateRange = weekStartDate + " ~ " + weekEndDate;
        String title = "第" + weekNumber + "周回顾";

        // Assemble V2 VO
        WeeklyReviewV2VO vo = new WeeklyReviewV2VO();
        vo.userId = userId;
        vo.title = title;
        vo.dateRange = dateRange;
        vo.weekStartDate = weekStartDate.toString();
        vo.weekEndDate = weekEndDate.toString();
        vo.topThemes = topThemes;
        vo.memoryCount = weekMemories.size();
        vo.dominantEmotion = dominantEmotion;
        vo.emotionSpectrum = emotionSpectrum;
        vo.intensityAverage = Math.round(intensityAverage * 100.0) / 100.0;
        vo.todoRatio = todoRatio;
        vo.recommendation = recommendation;
        vo.auroraObservation = auroraObservation;
        vo.dailySnapshots = dailySnapshots;
        vo.legacy = false;

        return vo;
    }

    /**
     * Get the latest V2 review for a user (returns V2 VO even if only V1 exists in DB).
     */
    public WeeklyReviewV2VO latest(Long userId) {
        WeeklyReview existing = weeklyReviewMapper.selectOne(
                new QueryWrapper<WeeklyReview>()
                        .eq("user_id", userId)
                        .orderByDesc("week_start_date")
                        .last("LIMIT 1"));

        if (existing == null) {
            return null;
        }

        // Check if existing review already has V2 fields populated
        if (existing.weekStartDate != null && isNotBlank(existing.title)) {
            WeeklyReviewV2VO vo = new WeeklyReviewV2VO();
            vo.id = existing.id;
            vo.userId = existing.userId;
            vo.title = existing.title;
            vo.weekStartDate = existing.weekStartDate.toString();
            vo.weekEndDate = existing.weekEndDate.toString();
            vo.dateRange = existing.weekStartDate + " ~ " + existing.weekEndDate;
            vo.dominantEmotion = existing.dominantTheme;
            vo.auroraObservation = existing.auroraObservation;
            vo.legacy = true;
            return vo;
        }

        // Upgrade to V2
        return generateForRange(userId, existing.weekStartDate, existing.weekEndDate);
    }

    /**
     * Save V2 review (persists to existing tb_weekly_review table with V2 fields).
     */
    @Transactional
    public WeeklyReviewV2VO save(WeeklyReviewV2VO vo) {
        LocalDate weekStartDate = LocalDate.parse(vo.weekStartDate);
        LocalDate weekEndDate = LocalDate.parse(vo.weekEndDate);

        WeeklyReview existing = weeklyReviewMapper.selectOne(
                new QueryWrapper<WeeklyReview>()
                        .eq("user_id", vo.userId)
                        .eq("week_start_date", weekStartDate));

        WeeklyReview review = existing != null ? existing : new WeeklyReview();
        review.userId = vo.userId;
        review.weekStartDate = weekStartDate;
        review.weekEndDate = weekEndDate;
        review.title = vo.title;
        review.dominantTheme = vo.dominantEmotion;
        review.themeSummary = vo.topThemes;
        review.emotionTrend = vo.emotionSpectrum;
        review.auroraObservation = vo.auroraObservation;
        review.status = "GENERATED";

        if (existing != null) {
            weeklyReviewMapper.updateById(review);
        } else {
            weeklyReviewMapper.insert(review);
        }

        vo.id = review.id;
        return vo;
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private List<WeeklyDailySnapshotVO> buildDailySnapshots(List<DailyRecord> records,
                                                             LocalDate weekStart, LocalDate endDate) {
        Map<LocalDate, DailyRecord> recordMap = records.stream()
                .collect(Collectors.toMap(r -> r.recordDate, r -> r, (a, b) -> a));

        List<WeeklyDailySnapshotVO> snapshots = new ArrayList<>();
        LocalDate current = weekStart;
        while (!current.isAfter(endDate)) {
            DailyRecord record = recordMap.get(current);
            WeeklyDailySnapshotVO snap = new WeeklyDailySnapshotVO();
            snap.date = current.toString();
            snap.dayLabel = current.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.CHINA);
            snap.emotionWeather = record != null ? record.emotionWeather : null;
            snap.theme = record != null ? record.theme : null;
            snap.cognitiveSummary = record != null ? record.cognitiveSummary : null;
            snap.auroraSummary = record != null ? record.auroraSummary : null;
            snapshots.add(snap);
            current = current.plusDays(1);
        }
        return snapshots;
    }

    private String buildTopThemes(List<MemoryCard> memories) {
        if (memories.isEmpty()) {
            return "本周暂无记录";
        }
        Map<String, Long> themeCounts = new HashMap<>();
        for (MemoryCard card : memories) {
            if (card.title != null) {
                themeCounts.merge(card.title, 1L, Long::sum);
            }
        }
        return themeCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(", "));
    }

    private String buildDominantEmotion(List<EmotionTimeline> timelines) {
        return timelines.stream()
                .filter(t -> t.dominantEmotion != null)
                .collect(Collectors.groupingBy(t -> t.dominantEmotion, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("平静");
    }

    private String buildEmotionSpectrum(List<EmotionTimeline> timelines) {
        if (timelines.isEmpty()) {
            return "[]";
        }
        Map<String, Long> counts = timelines.stream()
                .filter(t -> t.dominantEmotion != null)
                .collect(Collectors.groupingBy(EmotionTimeline::getDominantEmotion, Collectors.counting()));

        int total = timelines.size();
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> String.format(Locale.US,
                        "{\"emotion\":\"%s\",\"ratio\":%.2f}", e.getKey(), (double)e.getValue() / total))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private String buildTodoRatio(List<TodoItem> allTodos, LocalDate weekStart, LocalDate weekEnd) {
        long total = allTodos.size();
        long completed = allTodos.stream()
                .filter(t -> "DONE".equals(t.status))
                .count();
        return completed + "/" + total;
    }

    private String buildRecommendation(EmotionPatternVO pattern, String dominantEmotion, int memoryCount) {
        if (pattern == null) {
            if ("平静".equals(dominantEmotion)) {
                return "这一周你保持着平静的状态。保持这个节奏就好，如果有想深入的话题，可以主动开启一段对话。";
            }
            return "这一周留下了" + memoryCount + "条记忆。无论是清晰还是模糊的感受，都值得被记住。";
        }

        return switch (pattern.patternType) {
            case "WEEKLY" -> "你有一个每周规律出现的情绪模式：" + pattern.emotion + "。建议在下周类似时间点前给自己留出一点缓冲空间。";
            case "RECURRING" -> "你近期反复经历" + pattern.emotion + "相关的感受。这可能是一个值得深入探索的线索。";
            default -> "这一周你的主导情绪是" + dominantEmotion + "。"
                    + (pattern.triggerScenes != null && !pattern.triggerScenes.isEmpty()
                        ? "它往往与" + pattern.triggerScenes.get(0) + "相关。"
                        : "");
        };
    }

    private String buildAuroraObservation(List<DailyRecord> records, List<EmotionTimeline> timelines) {
        if (records.isEmpty() && timelines.isEmpty()) {
            return "本周暂无极光观察记录。继续和 Aurora 对话，让我更好地了解你。";
        }

        StringBuilder sb = new StringBuilder();
        long activeDays = records.stream().filter(r -> r.auroraSummary != null && !r.auroraSummary.isBlank()).count();
        if (activeDays > 0) {
            sb.append("本周").append(activeDays).append("天你分享了内心状态");
        } else {
            sb.append("本周你较少主动分享");
        }

        String dominantEmotion = buildDominantEmotion(timelines);
        if (!"平静".equals(dominantEmotion)) {
            sb.append("，情绪以").append(dominantEmotion).append("为主");
        }
        sb.append("。 Aurora 会继续在这里等你。");

        return sb.toString();
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}