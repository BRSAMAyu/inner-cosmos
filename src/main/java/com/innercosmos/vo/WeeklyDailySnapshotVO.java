package com.innercosmos.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Daily snapshot within a WeeklyReviewV2.
 * Represents aggregated data for a single day of the week.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyDailySnapshotVO {
    /** ISO date string e.g. "2026-06-01" */
    public String date;

    /** Day of week label e.g. "周一", "周二" */
    public String dayLabel;

    /** Emotion weather type for this day */
    public String emotionWeather;

    /** Dominant theme for this day */
    public String theme;

    /** Key memory summary for this day */
    public String memorySummary;

    /** Cognitive summary for this day */
    public String cognitiveSummary;

    /** Task completion ratio e.g. "2/5" */
    public String taskRatio;

    /** Aurora observation for this day */
    public String auroraSummary;
}