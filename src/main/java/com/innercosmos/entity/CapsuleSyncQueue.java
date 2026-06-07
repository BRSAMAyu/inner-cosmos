package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("tb_capsule_sync_queue")
public class CapsuleSyncQueue extends BaseEntity {
    public Long userId;
    public Long capsuleId;
    public String status;          // PENDING, APPROVED, REJECTED
    public String proposedContextDiff;  // TEXT - JSON describing the proposed changes
    public LocalDateTime createdAt;
    public LocalDateTime decidedAt;
}