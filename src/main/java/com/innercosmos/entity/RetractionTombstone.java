package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * CP-15 immutable retraction marker. Independent of business rows on purpose: a row restored
 * from an old backup stays unreadable because this marker (retained in the independent
 * rights ledger) still blocks it. The row id doubles as the monotonic rights watermark.
 */
@TableName("tb_retraction_tombstone")
public class RetractionTombstone extends BaseEntity {
    /** Subject constants mirror DataRetractionReceiptService (MEMORY / CAPSULE / DATA_USE_GRANT). */
    public String subjectType;
    public Long subjectId;
    public Long ownerUserId;
    public String consentVersion;
    public String reason;
    public LocalDateTime appliedAt;
}
