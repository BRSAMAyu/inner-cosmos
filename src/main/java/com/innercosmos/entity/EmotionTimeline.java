package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;

/**
 * Emotion Timeline entity representing daily emotional aggregates.
 * Provides emotion-over-time visualization and trend analysis.
 */
@TableName("tb_emotion_timeline")
public class EmotionTimeline extends BaseEntity {
    public Long userId;
    public LocalDate recordDate;
    public String dominantEmotion;
    public String emotionSpectrum;
    public Double intensityAverage;
    public String triggerSummary;
    public Integer memoryCount;

    /**
     * Trend point data class for visualization.
     */
    public static class TrendPoint {
        public LocalDate date;
        public String emotion;
        public double intensity;

        public TrendPoint(LocalDate date, String emotion, double intensity) {
            this.date = date;
            this.emotion = emotion;
            this.intensity = intensity;
        }
    }
}
