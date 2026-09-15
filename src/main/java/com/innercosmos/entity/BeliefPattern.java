package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Belief Pattern entity representing recurring beliefs extracted from user memories.
 * Captures cognitive patterns, beliefs, and their evolution over time.
 */
@TableName("tb_belief_pattern")
public class BeliefPattern extends BaseEntity {
    private static final Logger log = LoggerFactory.getLogger(BeliefPattern.class);

    /**
     * Inclusive valid range for {@link #strengthScore}. Belief strength is on a
     * normalized 0..1 scale (schema default 0.5; recalculateStrength caps at
     * Math.min(1.0, ...)). Any value outside [MIN_STRENGTH, MAX_STRENGTH] is
     * clamped to the nearest bound at write time (VS-006).
     */
    public static final double MIN_STRENGTH = 0.0;
    public static final double MAX_STRENGTH = 1.0;

    public Long userId;
    public String beliefContent;
    public String beliefType;
    public String beliefCategory;
    public Double strengthScore;
    public String supportingMemoryIds;
    public String contradictingMemoryIds;
    public LocalDateTime firstDetectedAt;
    public LocalDateTime lastConfirmedAt;
    public Integer confirmationCount;
    public String status;

    /**
     * CP-21 optimistic-lock token (V54 / schema.sql twin, column default 1). Every
     * {@code recalculateStrength} transition bumps it atomically; callers that rendered
     * a belief row pin the version they saw and a stale pin surfaces as
     * {@link com.innercosmos.common.ErrorCode#CONFLICT}. Null only for rows read before
     * the column existed — treat null as 1.
     */
    public Integer version;

    /**
     * Clamp a raw strength score into the valid [0,1] range, logging a warning
     * when the input was out of range. Use this at every write site instead of
     * assigning {@link #strengthScore} directly so the invariant is enforced
     * regardless of caller. Null input returns null (no score available).
     */
    public static Double clampStrength(Double raw) {
        if (raw == null) {
            return null;
        }
        if (raw < MIN_STRENGTH) {
            log.warn("BeliefPattern.strengthScore out of range ({}), clamped to {}", raw, MIN_STRENGTH);
            return MIN_STRENGTH;
        }
        if (raw > MAX_STRENGTH) {
            log.warn("BeliefPattern.strengthScore out of range ({}), clamped to {}", raw, MAX_STRENGTH);
            return MAX_STRENGTH;
        }
        return raw;
    }
}
