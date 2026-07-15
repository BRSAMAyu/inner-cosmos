package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** Stable external identity binding. Email is informational and never an identity key. */
@TableName("tb_user_identity")
public class UserIdentity extends BaseEntity {
    public Long userId;
    public String issuer;
    public String subject;
    public String emailSnapshot;
    public LocalDateTime lastAuthenticatedAt;
}
