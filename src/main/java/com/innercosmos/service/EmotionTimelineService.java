package com.innercosmos.service;

import com.innercosmos.entity.EmotionTimeline;
import java.time.LocalDate;
import java.util.List;

/**
 * Service for emotion timeline tracking and trend analysis.
 * Provides daily emotion aggregates and visualization data.
 */
public interface EmotionTimelineService {
    /**
     * Aggregate emotions for a specific date from all memory cards created that day.
     */
    void aggregateForDate(Long userId, LocalDate date);

    /**
     * Get emotion timeline entries for a date range.
     */
    List<EmotionTimeline> getTimeline(Long userId, LocalDate startDate, LocalDate endDate);

    /**
     * Get today's emotion entry (creating if doesn't exist).
     */
    EmotionTimeline getToday(Long userId);

    /**
     * Get emotion trend data for visualization (last N days).
     */
    List<EmotionTimeline.TrendPoint> getTrend(Long userId, int days);

    /**
     * Find dominant emotion patterns over time.
     */
    List<String> findDominantPatterns(Long userId, int days);

    /**
     * Calculate emotion stability score (0-1, higher = more stable).
     */
    double calculateStability(Long userId, int days);
}
