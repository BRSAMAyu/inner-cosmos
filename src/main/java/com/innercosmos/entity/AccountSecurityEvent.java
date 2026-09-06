package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

/** CP-13 account-security audit trail (device revocations, freezes, verification upgrades). */
@TableName("tb_account_security_event")
public class AccountSecurityEvent extends BaseEntity {
    public Long userId;
    public String action;
    /** Who performed the action; null = the account owner. */
    public Long actorId;
    public String detail;
}
