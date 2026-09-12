package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** CP-20 durable cross-session risk continuity: one row per user with explicit decay. */
@TableName("tb_user_risk_state")
public class UserRiskState extends BaseEntity {
    public Long userId;
    /** NONE, WATCH or ELEVATED (an explicit crisis event is an intervention, not a level). */
    public String level;
    public Double score;
    public LocalDateTime lastObservedAt;
    public Long lastSafetyEventId;
}
