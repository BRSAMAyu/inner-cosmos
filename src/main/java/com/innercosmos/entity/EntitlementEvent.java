package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * CP-46 entitlement transition audit. {@code channelNotificationId} is UNIQUE — a
 * duplicated channel notification (支付重复通知) acks the existing row and never applies
 * its transition twice. Transitions: GRANT_TRIAL, ACTIVATE, RENEW, ENTER_GRACE, CANCEL,
 * EXPIRE, REVOKE_REFUND, RESTORE, USER_CANCEL.
 */
@TableName("tb_entitlement_event")
public class EntitlementEvent extends BaseEntity {
    public String channelNotificationId;
    public Long entitlementId;
    public Long userId;
    public String transition;
    public String fromState;
    public String toState;
    public LocalDateTime occurredAt;
}
