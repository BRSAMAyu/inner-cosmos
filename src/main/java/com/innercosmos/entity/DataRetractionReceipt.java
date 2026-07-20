package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

/**
 * A5 / G5 PROFILE-PROPAGATION: an auditable, sensitive-payload-free receipt of what a user's
 * data-rights action (memory forget, capsule archive/withdrawal, consent-grant revocation) did to
 * a specific derivative of that source data.
 *
 * <p>Unlike {@link MemoryProjectionReceipt} — which is keyed to a {@code tb_memory_operation} and
 * only covers memory-operation projections — this receipt spans the consent/erasure path, whose
 * subject can be a memory, a capsule or a data-use grant, and whose derivatives include the capsule
 * matching index that previously had no invalidation hook at all. It is append-only audit: it never
 * stores memory text, persona prompts, embeddings or any other sensitive payload, only the count of
 * derivative rows affected and a short human-readable reason.</p>
 */
@TableName("tb_data_retraction_receipt")
public class DataRetractionReceipt extends BaseEntity {
    /** Owner whose data was retracted; every read of this table must stay owner-scoped. */
    public Long userId;
    /** MEMORY | CAPSULE | DATA_USE_GRANT — what the owner acted on. */
    public String subjectType;
    /** Identifier of the subject within {@link #subjectType}. */
    public Long subjectId;
    /** CAPSULE_MATCH_INDEX | CAPSULE_PERSONA | GENOME | MEMORY_EMBEDDING — the derivative touched. */
    public String derivativeType;
    /** ERASED | REVIEW_REQUIRED | CLEARED — what happened to the derivative. */
    public String action;
    /** How many derivative rows the action affected (0 is honest for an idempotent re-run). */
    public Integer affectedCount;
    /** Short, non-sensitive reason string; never contains source content. */
    public String reason;
}
