package com.innercosmos.service;

import com.innercosmos.entity.CapsuleGenomeVersion;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.MemoryCard;

import java.util.List;

public interface CapsuleGenomeService {
    CapsuleGenomeVersion compile(EchoCapsule capsule, List<MemoryCard> authorizedCards, String reason);
    CapsuleGenomeVersion current(Long capsuleId);
    List<CapsuleGenomeVersion> history(Long ownerUserId, Long capsuleId);
    void markNeedsReview(Long capsuleId, String reason);
    void withdraw(Long capsuleId, String reason);
}
