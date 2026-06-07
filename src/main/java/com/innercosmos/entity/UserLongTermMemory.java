package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("tb_user_long_term_memory")
public class UserLongTermMemory extends BaseEntity {
    public Long userId;
    public String factType;
    public String factValue;
    public Long sourceSessionId;
    public Double confidence;
    public String privacyLevel;
    public Boolean userApproved;
    public LocalDateTime createdAt;
}