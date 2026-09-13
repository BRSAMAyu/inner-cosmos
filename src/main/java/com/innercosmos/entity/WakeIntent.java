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
    public Long contextSessionId;
    public Long contextMessageId;
    public Long supersedesIntentId;
    public String content;
    public String status;
    public String decisionPolicyVersion;
    public String claimToken;
    public String claimedBy;
    public LocalDateTime claimUntil;
    /**
     * CP-26 quiet hours: when a due intent is withheld inside a quiet window, the row keeps a
     * visible DEFERRED status and this field carries the (UTC) instant the quiet window ends,
     * after which the claim scan picks it up again. Never null for live rows — a defer is
     * always explained on the row itself instead of silently re-polled or dropped.
     */
    public LocalDateTime deferredUntil;
    public String outcome;
    public String outcomeReason;
    public LocalDateTime firedAt;
    public LocalDateTime cancelledAt;
    public String userFeedback;
    public LocalDateTime feedbackAt;
}
