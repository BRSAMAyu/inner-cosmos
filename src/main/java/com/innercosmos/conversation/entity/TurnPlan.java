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
    /** Allow-listed side effect proposed by this turn; never executed before explicit confirmation. */
    public String proposedActionType;
    /** Owner-private JSON data for the proposed action. Model output is validated before it enters here. */
    public String proposedActionPayload;
    /** Human-readable, bounded confirmation summary. */
    public String proposedActionSummary;
    /** PENDING_CONFIRMATION / EXECUTED / CANCELLED / EXPIRED / SUPERSEDED. */
    public String actionStatus;
    public LocalDateTime actionConfirmedAt;
    /** Opaque reference such as memory:42 or wake-intent:17; never stores credentials. */
    public String actionResultRef;
    public LocalDateTime committedAt;
    public LocalDateTime cancelledAt;
}
