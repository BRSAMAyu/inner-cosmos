package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("tb_capsule_sync_queue")
public class CapsuleSyncQueue extends BaseEntity {
    public Long userId;
    public Long capsuleId;
    public String status;          // PENDING, APPROVED, REJECTED, FAILED, SYNCED
    public String proposedContextDiff;  // TEXT - JSON describing the proposed changes
    public LocalDateTime createdAt;
    public LocalDateTime decidedAt;

    // IC-CAP-002 B-2: failure-visible sync + retry bookkeeping
    public Integer attemptCount;        // number of regeneration attempts so far
    public String lastError;            // last failure message (truncated)
    public LocalDateTime failedAt;      // when it last failed
    public LocalDateTime nextRetryAt;   // earliest time the retry job may re-run it
}