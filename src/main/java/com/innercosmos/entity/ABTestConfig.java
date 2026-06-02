package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * A/B Test Configuration entity.
 * Manages A/B testing settings for LLM provider comparison.
 */
@TableName("tb_ab_test_config")
public class ABTestConfig extends BaseEntity {
    public String testName;
    public String description;
    public Boolean enabled;
    public Integer mockPercentage; // 0-100, typically 50
    public String controlGroup; // MOCK or REMOTE
    public Long totalParticipants;
    public LocalDateTime startTime;
    public LocalDateTime endTime;
    public String status; // ACTIVE, PAUSED, COMPLETED
}
