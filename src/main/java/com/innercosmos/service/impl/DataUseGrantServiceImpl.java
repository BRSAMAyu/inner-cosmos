package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.entity.AuthorizedMemoryRef;
import com.innercosmos.entity.DataUseGrant;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.AuthorizedMemoryRefMapper;
import com.innercosmos.mapper.DataUseGrantMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.service.CapsuleEmbeddingIndexService;
import com.innercosmos.service.CapsuleGenomeService;
import com.innercosmos.service.DataRetractionReceiptService;
import com.innercosmos.service.DataUseGrantService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class DataUseGrantServiceImpl implements DataUseGrantService {
    private final DataUseGrantMapper grantMapper;
    private final EchoCapsuleMapper capsuleMapper;
    private final MemoryCardMapper memoryMapper;
    private final AuthorizedMemoryRefMapper refMapper;
    private final CapsuleGenomeService genomeService;
    private final CapsuleEmbeddingIndexService capsuleEmbeddingIndexService;
    private final DataRetractionReceiptService retractionReceiptService;

    public DataUseGrantServiceImpl(DataUseGrantMapper grantMapper, EchoCapsuleMapper capsuleMapper,
                                   MemoryCardMapper memoryMapper, AuthorizedMemoryRefMapper refMapper,
                                   CapsuleGenomeService genomeService,
                                   CapsuleEmbeddingIndexService capsuleEmbeddingIndexService,
                                   DataRetractionReceiptService retractionReceiptService) {
        this.grantMapper = grantMapper;
        this.capsuleMapper = capsuleMapper;
        this.memoryMapper = memoryMapper;
        this.refMapper = refMapper;
        this.genomeService = genomeService;
        this.capsuleEmbeddingIndexService = capsuleEmbeddingIndexService;
        this.retractionReceiptService = retractionReceiptService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DataUseGrant> authorize(EchoCapsule capsule, MemoryCard card) {
        String primary = Boolean.TRUE.equals(capsule.simulatorOnly) ? "CAPSULE_SIMULATOR" : "CAPSULE_RUNTIME";
        return List.of(create(capsule, card, primary), create(capsule, card, "PROVIDER_EGRESS"));
    }

    private DataUseGrant create(EchoCapsule capsule, MemoryCard card, String purpose) {
        DataUseGrant previous = grantMapper.selectOne(new QueryWrapper<DataUseGrant>()
                .eq("owner_user_id", capsule.ownerUserId).eq("resource_type", "MEMORY_CARD")
                .eq("resource_id", card.id).eq("purpose", purpose)
                .eq("consumer_type", "ECHO_CAPSULE").eq("consumer_id", capsule.id)
                .orderByDesc("grant_version").last("LIMIT 1"));
        DataUseGrant grant = new DataUseGrant();
        grant.ownerUserId = capsule.ownerUserId;
        grant.resourceType = "MEMORY_CARD";
        grant.resourceId = card.id;
        grant.resourceVersion = card.versionNo == null ? 1 : card.versionNo;
        grant.purpose = purpose;
        grant.consumerType = "ECHO_CAPSULE";
        grant.consumerId = capsule.id;
        grant.grantVersion = previous == null ? 1 : previous.grantVersion + 1;
        grant.parentGrantId = previous == null ? null : previous.id;
        grant.status = "ACTIVE";
        grant.consentSource = "OWNER_EXPLICIT_CAPSULE_SELECTION";
        grant.grantedAt = LocalDateTime.now();
        grantMapper.insert(grant);
        return grant;
    }

    @Override
    public void revokeForCapsule(Long capsuleId, String reason) {
        revokeMatching(new QueryWrapper<DataUseGrant>().eq("consumer_type", "ECHO_CAPSULE")
                .eq("consumer_id", capsuleId).eq("status", "ACTIVE"), reason);
    }

    @Override
    public void revokeForMemory(Long memoryId, String reason) {
        revokeMatching(new QueryWrapper<DataUseGrant>().eq("resource_type", "MEMORY_CARD")
                .eq("resource_id", memoryId).eq("status", "ACTIVE"), reason);
    }

    private void revokeMatching(QueryWrapper<DataUseGrant> query, String reason) {
        for (DataUseGrant grant : grantMapper.selectList(query)) revokeRow(grant, reason);
    }

    private void revokeRow(DataUseGrant grant, String reason) {
        if (!"ACTIVE".equals(grant.status)) return;
        grant.status = "REVOKED";
        grant.revokedAt = LocalDateTime.now();
        grant.revokeReason = reason == null || reason.isBlank() ? "OWNER_REVOKED" : reason;
        grantMapper.updateById(grant);
    }

    @Override
    public boolean authorizationsValid(EchoCapsule capsule, Set<Long> memoryIds) {
        if (memoryIds.isEmpty()) return true;
        String primary = Boolean.TRUE.equals(capsule.simulatorOnly) ? "CAPSULE_SIMULATOR" : "CAPSULE_RUNTIME";
        for (Long memoryId : memoryIds) {
            MemoryCard card = memoryMapper.selectById(memoryId);
            if (card == null || !capsule.ownerUserId.equals(card.userId) || !"ACTIVE".equalsIgnoreCase(card.status)) return false;
            int version = card.versionNo == null ? 1 : card.versionNo;
            for (String purpose : List.of(primary, "PROVIDER_EGRESS")) {
                long count = grantMapper.selectCount(new QueryWrapper<DataUseGrant>()
                        .eq("owner_user_id", capsule.ownerUserId).eq("resource_type", "MEMORY_CARD")
                        .eq("resource_id", memoryId).eq("resource_version", version)
                        .eq("purpose", purpose).eq("consumer_type", "ECHO_CAPSULE")
                        .eq("consumer_id", capsule.id).eq("status", "ACTIVE"));
                if (count != 1) return false;
            }
        }
        return true;
    }

    @Override
    public List<DataUseGrant> history(Long ownerUserId, Long capsuleId) {
        EchoCapsule capsule = capsuleMapper.selectById(capsuleId);
        if (capsule == null || !ownerUserId.equals(capsule.ownerUserId)) {
            throw new BusinessException("UNAUTHORIZED", "无权查看此共鸣体的数据使用授权");
        }
        return grantMapper.selectList(new QueryWrapper<DataUseGrant>()
                .eq("owner_user_id", ownerUserId).eq("consumer_type", "ECHO_CAPSULE")
                .eq("consumer_id", capsuleId).orderByDesc("id"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataUseGrant revoke(Long ownerUserId, Long capsuleId, Long grantId, String reason) {
        EchoCapsule capsule = capsuleMapper.selectById(capsuleId);
        DataUseGrant grant = grantMapper.selectById(grantId);
        if (capsule == null || grant == null || !ownerUserId.equals(capsule.ownerUserId)
                || !ownerUserId.equals(grant.ownerUserId) || !capsuleId.equals(grant.consumerId)) {
            throw new BusinessException("UNAUTHORIZED", "无权撤销此数据使用授权");
        }
        revokeRow(grant, reason);
        refMapper.update(null, new UpdateWrapper<AuthorizedMemoryRef>()
                .eq("capsule_id", capsuleId).eq("memory_card_id", grant.resourceId)
                .set("authorization_status", "WITHDRAWN"));
        capsuleMapper.update(null, new UpdateWrapper<EchoCapsule>().eq("id", capsuleId)
                .set("visibility_status", "NEEDS_REVIEW").set("is_public", false));
        genomeService.markNeedsReview(capsuleId, "data-use grant revoked by owner");
        // The capsule just left the public plaza, so its compiled matching vector must stop being a
        // discoverable candidate immediately — not merely wait for the next content-hash rebuild.
        int erased = capsuleEmbeddingIndexService.retireForCapsule(capsuleId);
        retractionReceiptService.record(ownerUserId, DataRetractionReceiptService.SUBJECT_DATA_USE_GRANT,
                grantId, DataRetractionReceiptService.DERIVATIVE_CAPSULE_MATCH_INDEX,
                DataRetractionReceiptService.ACTION_ERASED, erased, "data-use grant revoked by owner");
        return grant;
    }
}
