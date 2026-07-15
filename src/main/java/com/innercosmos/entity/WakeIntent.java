package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * Durable, explainable intent for Aurora to return to a user at an appropriate time.
 * The window is deliberately wider than a single timer so delivery can be re-planned
 * around the user's current boundaries without losing Aurora's initiative.
 */
@TableName("tb_wake_intent")
public class WakeIntent extends BaseEntity {
    public Long userId;
    public String purpose;
    public String reasonForUser;
    public LocalDateTime earliestAt;
    public LocalDateTime preferredAt;
    public LocalDateTime latestAt;
    public String timezone;
    public String preconditionsJson;
    public String cancelConditionsJson;
    public String payloadRef;
    public String content;
    public String status;
    public String decisionPolicyVersion;
    public String claimToken;
    public String claimedBy;
    public LocalDateTime claimUntil;
    public String outcome;
    public String outcomeReason;
    public LocalDateTime firedAt;
    public LocalDateTime cancelledAt;
}
