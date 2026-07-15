package com.innercosmos.service;

import com.innercosmos.dto.CapsuleSandboxFeedbackRequest;
import com.innercosmos.entity.CapsuleSandboxFeedback;
import com.innercosmos.vo.CapsuleFidelitySummaryVO;
import com.innercosmos.vo.CapsuleSandboxVO;

import java.util.List;

public interface CapsuleSandboxService {
    CapsuleSandboxVO respond(Long ownerUserId, Long capsuleId, String question);
    CapsuleSandboxFeedback recordFeedback(Long ownerUserId, Long capsuleId, CapsuleSandboxFeedbackRequest request);
    List<CapsuleSandboxFeedback> feedback(Long ownerUserId, Long capsuleId);

    /**
     * Aggregates sandbox feedback (LIKE_ME/NOT_ME/FACT_WRONG/TOO_EXPOSED/TONE_WRONG) per
     * Genome version into a visible fidelity signal, newest version first. Feedback was
     * previously write-only — recorded but never read back anywhere — so an owner had no way
     * to see whether a version was actually landing as "like me" before deciding to publish
     * or recompile. This does not gate publish; it only makes the existing signal visible.
     */
    List<CapsuleFidelitySummaryVO> fidelitySummary(Long ownerUserId, Long capsuleId);
}
