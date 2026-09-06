package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * CP-13 outcome row of an age/identity verification attempt. Holds the provider reference
 * and result only — credential images, raw codes and full personal numbers are never
 * persisted here (blueprint recovery clause).
 */
@TableName("tb_identity_verification")
public class IdentityVerification extends BaseEntity {
    public Long userId;
    /** VERIFIED_ID channel, e.g. OPERATOR_SMS / ALIPAY_CERTIFIED / MANUAL_REVIEW. */
    public String method;
    public String provider;
    /** Provider-side opaque reference; unique so replays collapse to one row. */
    public String providerReference;
    /** PENDING, VERIFIED, REJECTED or EXPIRED. */
    public String status;
    public LocalDate verifiedBirthDate;
    public String failureReason;
    public LocalDateTime verifiedAt;
    /** Challenge expiry — expired codes must fail, not auto-extend. */
    public LocalDateTime expiresAt;
    /** Set when the outcome is consumed into the account (one-shot, replay-proof). */
    public LocalDateTime consumedAt;
}
