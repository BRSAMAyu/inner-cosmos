package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * A/B Test Metrics entity.
 * Tracks performance metrics for A/B testing groups.
 */
@TableName("tb_ab_test_metrics")
public class ABTestMetrics extends BaseEntity {
    public Long userId;
    public String testName;
    public String assignedGroup; // MOCK or REMOTE
    public String moduleName;
    public Integer requestCount;
    public Double avgLatency; // milliseconds
    public Integer successCount;
    public Integer fallbackCount;
    public Double successRate;
    public LocalDateTime lastRequestAt;
}
