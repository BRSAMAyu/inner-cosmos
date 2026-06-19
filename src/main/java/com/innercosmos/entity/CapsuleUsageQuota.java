package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;

@TableName("tb_capsule_usage_quota")
public class CapsuleUsageQuota extends BaseEntity {
    public Long visitorUserId;
    public Long capsuleId;
    public LocalDate quotaDate;
    public Integer turnCount;
}
