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

    /**
     * IC-EMO-003 (additive): aggregate a day's emotion from enriched
     * {@link com.innercosmos.entity.EmotionTrace} rows (deterministic, no LLM) and
     * upsert the timeline row for that date. Complements — does not replace — the
     * MemoryCard-based {@link #aggregateForDate}. No traces for the day => no-op.
     */
    void aggregateFromTraces(Long userId, LocalDate date);

    /**
     * IC-EMO-003 (additive): the visualization view — trend points plus the
     * mid-term emotion baseline and a stability score derived from enriched trace
     * data. Backward-compatible; existing getters are unchanged.
     */
    EmotionTimelineView getTimelineView(Long userId, int days);

    /**
     * View VO for the enriched timeline visualization: the trend series for the
     * chart, the computed {@link com.innercosmos.ai.semantic.EmotionBaseline}, and a
     * convenience {@code stabilityScore} mirror of {@code baseline.stabilityScore}.
     */
    class EmotionTimelineView {
        public List<EmotionTimeline.TrendPoint> trend;
        public com.innercosmos.ai.semantic.EmotionBaseline baseline;
        public double stabilityScore;
    }
}
