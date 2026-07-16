package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("tb_data_use_grant")
public class DataUseGrant extends BaseEntity {
    public Long ownerUserId;
    public String resourceType;
    public Long resourceId;
    public Integer resourceVersion;
    public String purpose;
    public String consumerType;
    public Long consumerId;
    public Integer grantVersion;
    public Long parentGrantId;
    public String status;
    public String consentSource;
    public LocalDateTime grantedAt;
    public LocalDateTime revokedAt;
    public String revokeReason;
}
