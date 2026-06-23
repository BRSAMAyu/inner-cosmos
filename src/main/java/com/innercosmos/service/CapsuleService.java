package com.innercosmos.service;

import com.innercosmos.dto.CapsuleCreateRequest;
import com.innercosmos.entity.CapsuleBoundary;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.vo.CapsulePreviewVO;
import java.util.List;
import java.util.Map;

public interface CapsuleService {
    EchoCapsule createFromMemory(Long userId, CapsuleCreateRequest request);

    EchoCapsule getOwnedCapsule(Long userId, Long capsuleId);

    EchoCapsule updateVisibility(Long userId, Long capsuleId, String visibilityStatus, Boolean isPublic);

    List<EchoCapsule> myCapsules(Long userId);

    List<EchoCapsule> plazaCapsules();

    List<Map<String, Object>> matchedCapsules(Long userId);

    CapsulePreviewVO previewUserMirror(Long userId);

    EchoCapsule updateContext(Long userId, Long capsuleId, Map<String, Object> body);

    Map<String, Object> contextPreview(Long userId, Long capsuleId);

    CapsuleBoundary getBoundary(Long userId, Long capsuleId);

    void updateBoundary(Long userId, Long capsuleId, CapsuleBoundary boundary);

    void archiveCapsule(Long userId, Long capsuleId);

    Double markLanded(Long userId, Long capsuleId);
}
