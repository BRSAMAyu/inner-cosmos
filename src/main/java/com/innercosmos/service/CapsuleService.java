package com.innercosmos.service;

import com.innercosmos.dto.CapsuleCreateRequest;
import com.innercosmos.entity.CapsuleBoundary;
import com.innercosmos.entity.EchoCapsule;
import java.util.List;

public interface CapsuleService {
    EchoCapsule createFromMemory(Long userId, CapsuleCreateRequest request);

    EchoCapsule getOwnedCapsule(Long userId, Long capsuleId);

    EchoCapsule updateVisibility(Long userId, Long capsuleId, String visibilityStatus, Boolean isPublic);

    List<EchoCapsule> myCapsules(Long userId);

    List<EchoCapsule> plazaCapsules();

    CapsuleBoundary getBoundary(Long capsuleId);

    void updateBoundary(Long userId, Long capsuleId, CapsuleBoundary boundary);

    void archiveCapsule(Long userId, Long capsuleId);
}
