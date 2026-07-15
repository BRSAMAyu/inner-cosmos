package com.innercosmos.service;

import com.innercosmos.dto.CapsuleSandboxFeedbackRequest;
import com.innercosmos.entity.CapsuleSandboxFeedback;
import com.innercosmos.vo.CapsuleSandboxVO;

import java.util.List;

public interface CapsuleSandboxService {
    CapsuleSandboxVO respond(Long ownerUserId, Long capsuleId, String question);
    CapsuleSandboxFeedback recordFeedback(Long ownerUserId, Long capsuleId, CapsuleSandboxFeedbackRequest request);
    List<CapsuleSandboxFeedback> feedback(Long ownerUserId, Long capsuleId);
}
