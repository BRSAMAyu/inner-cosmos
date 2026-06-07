package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.entity.EmotionTimeline;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.mapper.EmotionTimelineMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.service.EmotionTimelineService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of EmotionTimelineService with LLM-based emotion aggregation.
 */
@Service
public class EmotionTimelineServiceImpl implements EmotionTimelineService {
    private final StructuredAiService structuredAiService;
    private final EmotionTimelineMapper emotionTimelineMapper;
    private final MemoryCardMapper memoryCardMapper;

    public EmotionTimelineServiceImpl(
            StructuredAiService structuredAiService,
            EmotionTimelineMapper emotionTimelineMapper,
            MemoryCardMapper memoryCardMapper) {
        this.structuredAiService = structuredAiService;
        this.emotionTimelineMapper = emotionTimelineMapper;
        this.memoryCardMapper = memoryCardMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void aggregateForDate(Long userId, LocalDate date) {
        // Find all memory cards created on this date
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        QueryWrapper<MemoryCard> cardQuery = new QueryWrapper<>();
        cardQuery.eq("user_id", userId)
                 .eq("status", "ACTIVE")
                 .ge("created_at", startOfDay)
                 .lt("created_at", endOfDay);

        List<MemoryCard> cards = memoryCardMapper.selectList(cardQuery);
        if (cards.isEmpty()) {
            return;
        }

        // Build emotion aggregation prompt
        String prompt = buildEmotionAggregationPrompt(cards);

        try {
            EmotionAggregationResult result = structuredAiService.call(
                userId, "EMOTION_AGGREGATE", prompt,
                Map.of("cardCount", cards.size(), "date", date.toString()),
                EmotionAggregationResult.class,
                () -> fallbackEmotionAggregation(cards)
            );

            // Create or update timeline entry
            QueryWrapper<EmotionTimeline> timelineQuery = new QueryWrapper<>();
            timelineQuery.eq("user_id", userId).eq("record_date", date);
            EmotionTimeline existing = emotionTimelineMapper.selectOne(timelineQuery);

            if (existing != null) {
                existing.dominantEmotion = result.dominantEmotion;
                existing.emotionSpectrum = result.emotionSpectrum;
                existing.intensityAverage = result.intensityAverage;
                existing.triggerSummary = result.triggerSummary;
                existing.memoryCount = cards.size();
                emotionTimelineMapper.updateById(existing);
            } else {
                EmotionTimeline timeline = new EmotionTimeline();
                timeline.userId = userId;
                timeline.recordDate = date;
                timeline.dominantEmotion = result.dominantEmotion;
                timeline.emotionSpectrum = result.emotionSpectrum;
                timeline.intensityAverage = result.intensityAverage;
                timeline.triggerSummary = result.triggerSummary;
                timeline.memoryCount = cards.size();
                emotionTimelineMapper.insert(timeline);
            }

        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(getClass()).error(
                    "Emotion aggregation failed for user {} date {}: {}", userId, date, e.getMessage(), e);
        }
    }

    @Override
    public List<EmotionTimeline> getTimeline(Long userId, LocalDate startDate, LocalDate endDate) {
        QueryWrapper<EmotionTimeline> query = new QueryWrapper<>();
        query.eq("user_id", userId)
             .ge("record_date", startDate)
             .le("record_date", endDate)
             .orderByAsc("record_date");
        return emotionTimelineMapper.selectList(query);
    }

    @Override
    public EmotionTimeline getToday(Long userId) {
        LocalDate today = LocalDate.now();
        QueryWrapper<EmotionTimeline> query = new QueryWrapper<>();
        query.eq("user_id", userId).eq("record_date", today);
        EmotionTimeline existing = emotionTimelineMapper.selectOne(query);

        if (existing != null) {
            return existing;
        }

        // Create default entry
        EmotionTimeline timeline = new EmotionTimeline();
        timeline.userId = userId;
        timeline.recordDate = today;
        timeline.dominantEmotion = "平静";
        timeline.emotionSpectrum = "[]";
        timeline.intensityAverage = 0.0;
        timeline.triggerSummary = "今日暂无情绪记录";
        timeline.memoryCount = 0;
        emotionTimelineMapper.insert(timeline);

        return timeline;
    }

    @Override
    public List<EmotionTimeline.TrendPoint> getTrend(Long userId, int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);

        List<EmotionTimeline> timelines = getTimeline(userId, startDate, endDate);

        return timelines.stream()
                .map(t -> new EmotionTimeline.TrendPoint(t.recordDate, t.dominantEmotion, t.intensityAverage))
                .collect(Collectors.toList());
    }

    @Override
    public List<String> findDominantPatterns(Long userId, int days) {
        List<EmotionTimeline> timelines = getTimeline(userId, LocalDate.now().minusDays(days), LocalDate.now());

        Map<String, Integer> emotionCounts = new HashMap<>();
        for (EmotionTimeline t : timelines) {
            if (t.dominantEmotion != null) {
                emotionCounts.merge(t.dominantEmotion, 1, Integer::sum);
            }
        }

        return emotionCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> e.getKey() + " (" + e.getValue() + "次)")
                .collect(Collectors.toList());
    }

    @Override
    public double calculateStability(Long userId, int days) {
        List<EmotionTimeline.TrendPoint> trends = getTrend(userId, days);
        if (trends.size() < 2) return 1.0;

        // Calculate variance in intensity
        double mean = trends.stream().mapToDouble(t -> t.intensity).average().orElse(0.5);
        double variance = trends.stream()
                .mapToDouble(t -> Math.pow(t.intensity - mean, 2))
                .average().orElse(0.0);

        // Convert variance to stability score (lower variance = higher stability)
        return Math.max(0, Math.min(1, 1 - variance));
    }

    private String buildEmotionAggregationPrompt(List<MemoryCard> cards) {
        StringBuilder sb = new StringBuilder();
        sb.append("分析以下记忆内容,聚合当天的情绪状态.\n\n");

        for (int i = 0; i < Math.min(5, cards.size()); i++) {
            MemoryCard card = cards.get(i);
            sb.append(String.format("记忆%d:%s - %s\n", i+1, card.title, card.summary));
        }

        sb.append("\n请提取:\n");
        sb.append("1. dominantEmotion - 主导情绪(如:平静、焦虑、愉悦、疲惫等)\n");
        sb.append("2. emotionSpectrum - 情绪光谱:包含主要情绪成分的JSON数组\n");
        sb.append("3. intensityAverage - 平均强度(0-1)\n");
        sb.append("4. triggerSummary - 触发事件摘要\n");

        sb.append("\n返回 JSON 格式:\n");
        sb.append("{\n");
        sb.append("  \"dominantEmotion\": \"主导情绪\",\n");
        sb.append("  \"emotionSpectrum\": \"[{\\\"emotion\\\":\\\"情绪\\\",\\\"ratio\\\":0.5}]\",\n");
        sb.append("  \"intensityAverage\": 0.5,\n");
        sb.append("  \"triggerSummary\": \"触发摘要\"\n");
        sb.append("}");

        return sb.toString();
    }

    private EmotionAggregationResult fallbackEmotionAggregation(List<MemoryCard> cards) {
        EmotionAggregationResult result = new EmotionAggregationResult();

        // Simple fallback based on emotion tags
        Map<String, Integer> emotionCounts = new HashMap<>();
        double totalIntensity = 0;

        for (MemoryCard card : cards) {
            if (card.emotionTags != null && !card.emotionTags.isEmpty()) {
                String tags = card.emotionTags.replaceAll("[\\[\\]\"]", "");
                for (String tag : tags.split(",")) {
                    String trimmed = tag.trim();
                    if (!trimmed.isEmpty()) {
                        emotionCounts.merge(trimmed, 1, Integer::sum);
                    }
                }
            }
            totalIntensity += card.intensityScore != null ? card.intensityScore : 0.3;
        }

        // Find dominant emotion
        result.dominantEmotion = emotionCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("平静");

        result.intensityAverage = cards.isEmpty() ? 0.0 : totalIntensity / cards.size();
        result.emotionSpectrum = emotionCounts.entrySet().stream()
                .map(e -> String.format("{\"emotion\":\"%s\",\"ratio\":%.2f}", e.getKey(),
                        (double)e.getValue() / cards.size()))
                .collect(Collectors.joining(",", "[", "]"));
        result.triggerSummary = String.format("来自%d条记忆的聚合", cards.size());

        return result;
    }

    private static class EmotionAggregationResult {
        public String dominantEmotion;
        public String emotionSpectrum;
        public Double intensityAverage;
        public String triggerSummary;
    }
}
