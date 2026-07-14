package com.innercosmos.conversation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.innercosmos.entity.BaseEntity;
import java.time.LocalDateTime;

@TableName("tb_turn_plan")
public class TurnPlan extends BaseEntity {
    public Long turnId;
    public Long userId;
    public Integer planVersion;
    /** Non-null only for the one effective committed plan; DB-unique per turn. */
    public Integer commitSlot;
    public String status;
    public String intent;
    public String posture;
    public String stopCondition;
    public LocalDateTime committedAt;
    public LocalDateTime cancelledAt;
}
