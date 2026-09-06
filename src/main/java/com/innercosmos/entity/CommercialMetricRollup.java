package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** Aggregate-only survival of a deleted account's metric events (CP-03 anonymization). */
@TableName("tb_commercial_metric_rollup")
public class CommercialMetricRollup extends BaseEntity {
    public String metricCode;
    public String anchorWeek;
    public Long anonymizedCount;
    public Long affectedTotal;
}
