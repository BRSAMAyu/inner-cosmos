package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@TableName("tb_emotion_trace")
public class EmotionTrace extends BaseEntity {
    private static final Logger log = LoggerFactory.getLogger(EmotionTrace.class);

    /**
     * Inclusive valid range for {@link #emotionScore}. The Inner Cosmos codebase
     * treats the emotion intensity on a 0..10 scale (see MemorySettlementServiceImpl
     * clamp(0,10) and EmotionTraceListener Math.min(10.0, ...)). Any value outside
     * [MIN_SCORE, MAX_SCORE] is clamped to the nearest bound at write time so an
     * out-of-range AI-extracted value can never be persisted (VS-006).
     */
    public static final double MIN_SCORE = 0.0;
    public static final double MAX_SCORE = 10.0;

    public Long userId;
    public Long sourceSessionId;
    public String emotionName;
    public Double emotionScore;
    public String weatherType;
    public String triggerScene;
    public LocalDate recordDate;

    /**
     * Clamp a raw emotion score into the valid [0,10] range, logging a warning
     * when the input was out of range. Use this at every write site instead of
     * assigning {@link #emotionScore} directly so the invariant is enforced
     * regardless of caller. Null input returns null (no score available).
     */
    public static Double clampScore(Double raw) {
        if (raw == null) {
            return null;
        }
        if (raw < MIN_SCORE) {
            log.warn("EmotionTrace.emotionScore out of range ({}), clamped to {}", raw, MIN_SCORE);
            return MIN_SCORE;
        }
        if (raw > MAX_SCORE) {
            log.warn("EmotionTrace.emotionScore out of range ({}), clamped to {}", raw, MAX_SCORE);
            return MAX_SCORE;
        }
        return raw;
    }
}
