package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** CP-36 trust-and-safety case: one report, one case, one SLA clock, full audit. */
@TableName("tb_moderation_case")
public class ModerationCase extends BaseEntity {
    public Long reportId;
    public String targetType;
    public Long targetId;
    /** P0 (1h) / P1 (24h) / P2 (72h) — derived from report content classes. */
    public String priority;
    /** OPEN / ASSIGNED / RESOLVED / DISMISSED / APPEALED. */
    public String status;
    public Long assigneeId;
    public LocalDateTime slaDueAt;
    public String resolution;
    public String appealNote;
}
