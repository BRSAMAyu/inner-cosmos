package com.innercosmos.event;

/**
 * A5 / G5: published when a data-retraction receipt is recorded (a memory/capsule/grant derivative was
 * erased or cleared by an owner data-rights action). Carries only the sensitive-free receipt facts so
 * a durable, replayable {@code data.retracted.v1} outbox row can be written in the same transaction
 * (see {@code DataRetractedOutboxWriter}). Having no listener is fine — when the outbox is disabled the
 * event is simply a no-op, and the synchronous in-transaction propagation is unchanged.
 */
public class DataRetractedEvent {
    public final Long receiptId;
    public final Long userId;
    public final String subjectType;
    public final Long subjectId;
    public final String derivativeType;
    public final String action;
    public final int affectedCount;

    public DataRetractedEvent(Long receiptId, Long userId, String subjectType, Long subjectId,
                              String derivativeType, String action, int affectedCount) {
        this.receiptId = receiptId;
        this.userId = userId;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.derivativeType = derivativeType;
        this.action = action;
        this.affectedCount = affectedCount;
    }
}
