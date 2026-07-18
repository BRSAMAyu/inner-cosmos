package com.innercosmos.service;

import com.innercosmos.vo.ClaimCandidateVO;
import com.innercosmos.vo.CorrectionConfirmationVO;
import java.util.List;

/**
 * Campaign B — the persistence + lifecycle layer over automatic claim extraction. Turns a finished
 * conversation into pending {@code CANDIDATE} claims the user can review, and promotes a confirmed
 * candidate into an authoritative {@code ACTIVE} claim through the existing user-correction path so
 * it inherits impact preview and downstream propagation. Auto-extraction never writes ACTIVE claims
 * directly — the user's confirmation is the authority boundary.
 */
public interface ClaimCandidateService {

    /**
     * Extract from the session's user messages and persist new candidates (idempotent per claim key).
     *
     * @return the number of candidates newly staged or refreshed.
     */
    int stageForSession(Long userId, Long sessionId);

    /** Pending candidates for the owner, newest first. */
    List<ClaimCandidateVO> listCandidates(Long userId);

    /** Promote a candidate to an ACTIVE claim via the correction confirm path; retires the candidate. */
    CorrectionConfirmationVO confirmCandidate(Long userId, Long candidateId);

    /** Dismiss a candidate the user rejects; it is marked DISMISSED, not hard-deleted. */
    void dismissCandidate(Long userId, Long candidateId);

    /**
     * Track A / A2 — global batch sweep (not owner-scoped; this is an internal maintenance pass, not
     * a user-facing read) that auto-dismisses {@code CANDIDATE} rows whose
     * {@link com.innercosmos.ai.claim.ClaimConfidenceDecayPolicy#effectiveConfidence} has decayed
     * below {@link com.innercosmos.ai.claim.ClaimConfidenceDecayPolicy#DISMISS_THRESHOLD}. Confirmed/
     * ACTIVE claims (explicit user assertions) are never touched — the query is scoped to
     * {@code status=CANDIDATE, sourceType=AUTO_EXTRACTION} only.
     *
     * @return the number of candidates dismissed as stale in this sweep.
     */
    int sweepStaleCandidates(int batchSize);
}
