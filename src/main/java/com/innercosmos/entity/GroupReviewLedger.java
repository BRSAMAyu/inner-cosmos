package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * CP-35 (V50): one structured per-group review (report) ledger row. The per-group host's
 * pending workload is the number of {@code status='PENDING'} rows, bounded by
 * {@code inner-cosmos.social.group-review-pending-capacity}; once bounded, NEW reports are
 * explicitly rejected and never persisted -- there is deliberately no PENDING_OVERFLOW
 * state (nothing silently queued, nothing silently dropped).
 */
@TableName("tb_group_review_ledger")
public class GroupReviewLedger extends BaseEntity {
    public Long groupId;
    public Long reporterUserId;
    public Long targetUserId;
    public Long targetMessageId;
    public String reason;
    /** PENDING -> RESOLVED | DISMISSED, flipped only by the group host. */
    public String status;
    public String resolutionNote;
    public Long resolvedBy;
    public LocalDateTime resolvedAt;
}
