package com.innercosmos.vo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * VO representing detected emotion patterns over a time window.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmotionPatternVO {
    /** Pattern type: WEEKLY, MONTHLY, TRIGGER, RECURRING */
    public String patternType;

    /** Human-readable pattern label */
    public String label;

    /** Emotion name associated with the pattern */
    public String emotion;

    /** Occurrence count within the window */
    public Integer count;

    /** Average intensity 0-1 */
    public Double intensityAverage;

    /** Date range for this pattern */
    public String dateRange;

    /** Key trigger scenes associated with this pattern */
    public List<String> triggerScenes = new ArrayList<>();

    /** Related memory titles */
    public List<String> relatedMemoryTitles = new ArrayList<>();

    /** Confidence score0-1 */
    public Double confidence;

    /** Start date of pattern window */
    public LocalDate startDate;

    /** End date of pattern window */
    public LocalDate endDate;
}