package com.innercosmos.vo;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * V2 weekly review VO with richer structure than the legacy entity.
 * Aggregates real data from DailyRecord, MemoryCard, TodoItem, and EmotionTimeline.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyReviewV2VO {
    public Long id;
    public Long userId;

    /** Title for the week e.g. "第24周回顾" */
    public String title;

    /** ISO date range string e.g. "2026-06-01 ~ 2026-06-07" */
    public String dateRange;

    /** Week start date (ISO) */
    public String weekStartDate;

    /** Week end date (ISO) */
    public String weekEndDate;

    /** Comma-separated top themes */
    public String topThemes;

    /** Total memory cards created this week */
    public Integer memoryCount;

    /** Dominant emotion label */
    public String dominantEmotion;

    /** Aggregated emotion spectrum JSON string */
    public String emotionSpectrum;

    /** Average emotion intensity 0-1 */
    public Double intensityAverage;

    /** Completed todos / total todos */
    public String todoRatio;

    /** LLM-generated narrative recommendation */
    public String recommendation;

    /** LLM-generated aurora observation for the week */
    public String auroraObservation;

    /** Per-day snapshots for the week */
    public List<WeeklyDailySnapshotVO> dailySnapshots = new ArrayList<>();

    /** True if this was generated from mock/placeholder data */
    public Boolean legacy = false;
}