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
}
