package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Belief Pattern entity representing recurring beliefs extracted from user memories.
 * Captures cognitive patterns, beliefs, and their evolution over time.
 */
@TableName("tb_belief_pattern")
public class BeliefPattern extends BaseEntity {
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
}
