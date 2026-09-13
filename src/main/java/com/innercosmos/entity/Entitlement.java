package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * CP-46 unified server-side entitlement, one row per (user, product) across channels.
 * {@code state} is the ONLY thing product code may consult for entitlement decisions;
 * channel-original subscription states are normalized into it by
 * {@code EntitlementStateServiceImpl} and every transition is audited (and deduplicated)
 * in {@link EntitlementEvent}. A refund flips the row to REVOKED (退款后撤销); a restore
 * can only revive a non-revoked row (恢复购买).
 */
@TableName("tb_entitlement")
public class Entitlement extends BaseEntity {
    /** PENDING_PAYMENT → TRIAL/ACTIVE → GRACE_PERIOD → CANCELLED → EXPIRED; REVOKED on refund. */
    public static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    public static final String TRIAL = "TRIAL";
    public static final String ACTIVE = "ACTIVE";
    public static final String GRACE_PERIOD = "GRACE_PERIOD";
    public static final String CANCELLED = "CANCELLED";
    public static final String EXPIRED = "EXPIRED";
    public static final String REVOKED = "REVOKED";

    public Long userId;
    public String productId;
    public String channel;
    public String channelOrderId;
    public String state;
    public LocalDateTime periodStart;
    public LocalDateTime periodEnd;
    public Boolean autoRenew;
    public Boolean cancelAtPeriodEnd;
}
