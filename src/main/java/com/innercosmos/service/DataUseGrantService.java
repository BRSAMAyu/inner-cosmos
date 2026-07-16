package com.innercosmos.service;

import com.innercosmos.entity.DataUseGrant;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.MemoryCard;

import java.util.List;
import java.util.Set;

public interface DataUseGrantService {
    List<DataUseGrant> authorize(EchoCapsule capsule, MemoryCard card);
    void revokeForCapsule(Long capsuleId, String reason);
    void revokeForMemory(Long memoryId, String reason);
    boolean authorizationsValid(EchoCapsule capsule, Set<Long> memoryIds);
    List<DataUseGrant> history(Long ownerUserId, Long capsuleId);
    DataUseGrant revoke(Long ownerUserId, Long capsuleId, Long grantId, String reason);
}
