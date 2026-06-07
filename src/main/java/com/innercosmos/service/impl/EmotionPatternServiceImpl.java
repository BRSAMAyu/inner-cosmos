package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.EmotionTimeline;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.mapper.EmotionTimelineMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.service.EmotionPatternService;
import com.innercosmos.vo.EmotionPatternVO;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of EmotionPatternService.
 * Detects emotion patterns by analyzing EmotionTimeline and related MemoryCards.
 */
@Service
public class EmotionPatternServiceImpl implements EmotionPatternService {

    private final EmotionTimelineMapper emotionTimelineMapper;
    private final MemoryCardMapper memoryCardMapper;

    public EmotionPatternServiceImpl(EmotionTimelineMapper emotionTimelineMapper,
                                     MemoryCardMapper memoryCardMapper) {
        this.emotionTimelineMapper = emotionTimelineMapper;
        this.memoryCardMapper = memoryCardMapper;
    }

    @Override
    public List<EmotionPatternVO> detectPatterns(Long userId, int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);

        List<EmotionTimeline> timelines = getTimeline(userId, startDate, endDate);
        if (timelines.isEmpty()) {
            return List.of();
        }

        // Group by emotion to find dominant patterns
        Map<String, List<EmotionTimeline>> byEmotion = timelines.stream()
                .filter(t -> t.dominantEmotion != null && !t.dominantEmotion.isBlank())
                .collect(Collectors.groupingBy(t -> t.dominantEmotion));

        List<EmotionPatternVO> patterns = new ArrayList<>();

        for (Map.Entry<String, List<EmotionTimeline>> entry : byEmotion.entrySet()) {
            EmotionPatternVO pattern = buildPattern(userId, entry.getKey(), entry.getValue(), startDate, endDate);
            if (pattern != null) {
                patterns.add(pattern);
            }
        }

        // Detect weekly recurring patterns (inline — method defined in interface)
        try {
            var weekly = detectWeeklyPatterns(userId, startDate, endDate);
            patterns.addAll(weekly);
        } catch (Exception ignored) { /* non-critical */ }

        // Sort by count descending
        patterns.sort((a, b) -> {
            int ca = a.count != null ? a.count : 0;
            int cb = b.count != null ? b.count : 0;
            return Integer.compare(cb, ca);
        });

        return patterns;
    }

    @Override
    public EmotionPatternVO getDominantPattern(Long userId, int days) {
        List<EmotionPatternVO> patterns = detectPatterns(userId, days);
        return patterns.isEmpty() ? null : patterns.get(0);
    }

    @Override
    public EmotionPatternVO getRangeSummary(Long userId, LocalDate startDate, LocalDate endDate) {
        List<EmotionTimeline> timelines = getTimeline(userId, startDate, endDate);
        if (timelines.isEmpty()) {
            return null;
        }

        // Find dominant emotion
        Map<String, Long> emotionCounts = timelines.stream()
                .filter(t -> t.dominantEmotion != null)
                .collect(Collectors.groupingBy(t -> t.dominantEmotion, Collectors.counting()));

        String dominantEmotion = emotionCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("平静");

        double avgIntensity = timelines.stream()
                .filter(t -> t.intensityAverage != null)
                .mapToDouble(t -> t.intensityAverage)
                .average()
                .orElse(0.5);

        EmotionPatternVO summary = new EmotionPatternVO();
        summary.patternType = "RANGE_SUMMARY";
        summary.label = dominantEmotion + "主导周";
        summary.emotion = dominantEmotion;
        summary.count = timelines.size();
        summary.intensityAverage = avgIntensity;
        summary.dateRange = startDate + " ~ " + endDate;
        summary.startDate = startDate;
        summary.endDate = endDate;
        summary.confidence = 0.8;

        // Aggregate trigger scenes
        List<String> triggers = timelines.stream()
                .filter(t -> t.triggerSummary != null && !t.triggerSummary.isBlank())
                .map(t -> t.triggerSummary)
                .distinct()
                .limit(5)
                .collect(Collectors.toList());
        summary.triggerScenes = triggers;

        return summary;
    }

    @Override
    public List<EmotionPatternVO> detectWeeklyPatterns(Long userId, LocalDate startDate, LocalDate endDate) {
        List<EmotionTimeline> timelines = getTimeline(userId, startDate, endDate);
        if (timelines.size() < 3) return List.of();

        Map<DayOfWeek, List<EmotionTimeline>> byDow = timelines.stream()
                .collect(Collectors.groupingBy(t -> t.recordDate.getDayOfWeek()));

        List<EmotionPatternVO> weekly = new ArrayList<>();
        for (Map.Entry<DayOfWeek, List<EmotionTimeline>> dow : byDow.entrySet()) {
            if (dow.getValue().size() >= 2) {
                String dowLabel = dow.getKey().getDisplayName(TextStyle.SHORT, Locale.CHINA);
                EmotionPatternVO p = new EmotionPatternVO();
                p.patternType = "WEEKLY";
                p.label = dowLabel + "情绪模式（每周规律）";
                p.emotion = dow.getValue().get(0).dominantEmotion;
                p.count = dow.getValue().size();
                p.intensityAverage = dow.getValue().stream()
                        .filter(t -> t.intensityAverage != null)
                        .mapToDouble(t -> t.intensityAverage).average().orElse(0.5);
                p.dateRange = startDate + " ~ " + endDate;
                p.startDate = startDate;
                p.endDate = endDate;
                p.confidence = Math.min(0.9, 0.5 + dow.getValue().size() * 0.05);
                weekly.add(p);
            }
        }
        return weekly;
    }

    private List<EmotionTimeline> getTimeline(Long userId, LocalDate startDate, LocalDate endDate) {
        QueryWrapper<EmotionTimeline> query = new QueryWrapper<>();
        query.eq("user_id", userId)
                .ge("record_date", startDate)
                .le("record_date", endDate)
                .orderByAsc("record_date");
        return emotionTimelineMapper.selectList(query);
    }

    private EmotionPatternVO buildPattern(Long userId, String emotion, List<EmotionTimeline> timelines,
                                          LocalDate startDate, LocalDate endDate) {
        if (timelines.size() < 2) {
            return null; // need at least 2 occurrences to form a pattern
        }

        double avgIntensity = timelines.stream()
                .filter(t -> t.intensityAverage != null)
                .mapToDouble(t -> t.intensityAverage)
                .average()
                .orElse(0.5);

        String patternType = determinePatternType(timelines);
        String label = emotion + "模式" + (patternType.equals("WEEKLY") ? "（每周规律）" : "（触发模式）");

        EmotionPatternVO pattern = new EmotionPatternVO();
        pattern.patternType = patternType;
        pattern.label = label;
        pattern.emotion = emotion;
        pattern.count = timelines.size();
        pattern.intensityAverage = avgIntensity;
        pattern.dateRange = startDate + " ~ " + endDate;
        pattern.startDate = startDate;
        pattern.endDate = endDate;
        pattern.confidence = Math.min(0.95, 0.5 + (timelines.size() * 0.05));

        // Gather trigger scenes
        List<String> triggers = timelines.stream()
                .filter(t -> t.triggerSummary != null && !t.triggerSummary.isBlank())
                .map(t -> t.triggerSummary)
                .distinct()
                .limit(5)
                .collect(Collectors.toList());
        pattern.triggerScenes = triggers;

        // Find related memory cards
        List<String> memoryTitles = findRelatedMemoryTitles(userId, timelines);
        pattern.relatedMemoryTitles = memoryTitles;

        return pattern;
    }

    private String determinePatternType(List<EmotionTimeline> timelines) {
        if (timelines.size() < 3) {
            return "TRIGGER";
        }

        // Check if emotion appears on same day of week
        long mondayCount = timelines.stream()
                .filter(t -> DayOfWeek.MONDAY.equals(t.recordDate.getDayOfWeek()))
                .count();
        if (mondayCount >= 2) {
            return "WEEKLY";
        }

        // Check if emotion appears with regular spacing
        if (timelines.size() >= 3) {
            List<LocalDate> dates = timelines.stream()
                    .map(t -> t.recordDate)
                    .sorted()
                    .collect(Collectors.toList());

            long gaps = 0;
            for (int i = 1; i < dates.size(); i++) {
                long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(dates.get(i - 1), dates.get(i));
                if (daysBetween == 7) {
                    gaps++;
                }
            }

            if (gaps >= 2) {
                return "RECURRING";
            }
        }

        return "TRIGGER";
    }

    private List<String> findRelatedMemoryTitles(Long userId, List<EmotionTimeline> timelines) {
        if (timelines.isEmpty()) {
            return List.of();
        }

        List<Long> memoryIds = new ArrayList<>();
        for (EmotionTimeline t : timelines) {
            if (t.memoryCount != null && t.memoryCount > 0) {
                // Find recent memory cards
                QueryWrapper<MemoryCard> query = new QueryWrapper<>();
                query.eq("user_id", userId)
                        .eq("status", "ACTIVE")
                        .orderByDesc("emotional_gravity")
                        .last("LIMIT 3");
                List<MemoryCard> cards = memoryCardMapper.selectList(query);
                for (MemoryCard card : cards) {
                    if (card.title != null && !memoryIds.contains(card.id)) {
                        memoryIds.add(card.id);
                    }
                }
            }
        }

        QueryWrapper<MemoryCard> query = new QueryWrapper<>();
        query.eq("user_id", userId)
                .eq("status", "ACTIVE")
                .orderByDesc("emotional_gravity")
                .last("LIMIT 5");
        List<MemoryCard> cards = memoryCardMapper.selectList(query);

        return cards.stream()
                .filter(c -> c.title != null)
                .map(c -> c.title)
                .limit(5)
                .collect(Collectors.toList());
    }
}