package com.innercosmos.service;

import com.innercosmos.entity.RelationCorrectionProposal;
import java.util.List;

/**
 * CP-34: both-party-consent corrections on a shared letter thread's relationship
 * understanding. State machine (terminal states never resurrect, transitions are
 * conditional single-row updates):
 *
 * <pre>
 * PROPOSED --accept(counterpart)-->  APPLIED
 * PROPOSED --reject(counterpart)-->  REJECTED
 * PROPOSED --withdraw(proposer)-->   WITHDRAWN
 * </pre>
 *
 * Only thread parties may propose; only the counterpart may decide; only the proposer
 * may withdraw; every other transition is refused with an explicit error (nothing
 * changes silently). APPLIED means the proposed value becomes the thread's shared
 * understanding for the corrected field (applied by the caller that owns that field).
 */
public interface RelationCorrectionService {

    /** Propose a correction on a shared thread. PROPOSED row; proposer must be a thread party. */
    RelationCorrectionProposal propose(Long userId, Long threadId, String correctionField,
                                       String proposedValue, String note);

    /** Counterpart accepts → APPLIED; the proposal row is returned with the applied field. */
    RelationCorrectionProposal accept(Long userId, Long proposalId);

    /** Counterpart rejects with a reason → REJECTED (terminal). */
    RelationCorrectionProposal reject(Long userId, Long proposalId, String reason);

    /** Proposer withdraws → WITHDRAWN (terminal). */
    RelationCorrectionProposal withdraw(Long userId, Long proposalId);

    /** Proposals the user has sent (optionally only open ones). */
    List<RelationCorrectionProposal> outgoing(Long userId, boolean openOnly);

    /** Proposals awaiting this user's decision. */
    List<RelationCorrectionProposal> incoming(Long userId);
}
