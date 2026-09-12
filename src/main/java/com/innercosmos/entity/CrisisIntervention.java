package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

/**
 * CP-20 auditable escalation step: actor, attempt/outcome, escalation path and the minimal
 * disclosure actually made — the blueprint's "每次有责任人、尝试／结果、升级和最小披露记录".
 */
@TableName("tb_crisis_intervention")
public class CrisisIntervention extends BaseEntity {
    public Long userId;
    public Long safetyEventId;
    /** NONE/WATCH/ELEVATED/EMERGENCY. */
    public String level;
    /** GENTLE_CHECK_IN / RESOURCES_SHOWN / WATCH_ESCALATED / EMERGENCY_PROTOCOL. */
    public String action;
    /** Who is responsible for following up; null = the automated runtime acted. */
    public Long responderId;
    public String outcome;
    public String escalation;
    public String minimalDisclosure;
}
