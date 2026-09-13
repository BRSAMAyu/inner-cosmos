package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * CP-34: 双方同意的关系纠错提案. A shared letter thread carries a relationship
 * understanding built from BOTH parties' data; one side cannot rewrite it alone. A
 * correction goes PROPOSED → APPLIED only when the counterpart accepts; REJECTED and
 * WITHDRAWN are terminal. No state is ever silently changed, and no terminal proposal
 * can be resurrected.
 */
@TableName("tb_relation_correction")
public class RelationCorrectionProposal extends BaseEntity {
    /** The shared thread this correction is about (both parties from tb_letter_thread). */
    public Long threadId;
    public Long proposerUserId;
    public Long counterpartUserId;
    /** What is being corrected, e.g. relationLabel / threadTitle (free-form field name). */
    public String correctionField;
    public String proposedValue;
    /** Optional proposer note explaining the correction. */
    public String note;
    /** PROPOSED / APPLIED / REJECTED / WITHDRAWN. */
    public String status;
    /** Counterpart's reason on REJECTED; null otherwise. */
    public String decisionReason;
    public LocalDateTime decidedAt;
}
