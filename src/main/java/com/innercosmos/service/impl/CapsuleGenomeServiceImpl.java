package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.entity.CapsuleGenomeVersion;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.CapsuleGenomeVersionMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.service.CapsuleGenomeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class CapsuleGenomeServiceImpl implements CapsuleGenomeService {
    static final String COMPILER_VERSION = "capsule-genome.v1";
    private final CapsuleGenomeVersionMapper genomeMapper;
    private final EchoCapsuleMapper capsuleMapper;
    private final ObjectMapper objectMapper;

    public CapsuleGenomeServiceImpl(CapsuleGenomeVersionMapper genomeMapper,
                                    EchoCapsuleMapper capsuleMapper,
                                    ObjectMapper objectMapper) {
        this.genomeMapper = genomeMapper;
        this.capsuleMapper = capsuleMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CapsuleGenomeVersion compile(EchoCapsule capsule, List<MemoryCard> authorizedCards, String reason) {
        if (capsule == null || capsule.id == null || capsule.ownerUserId == null) {
            throw new IllegalArgumentException("persisted owner-bound capsule is required");
        }
        List<CapsuleGenomeVersion> versions = genomeMapper.selectList(
                new QueryWrapper<CapsuleGenomeVersion>().eq("capsule_id", capsule.id)
                        .orderByDesc("version_no"));
        CapsuleGenomeVersion previous = versions.isEmpty() ? null : versions.getFirst();
        for (CapsuleGenomeVersion row : versions) {
            if ("ACTIVE".equals(row.status)) {
                row.status = "SUPERSEDED";
                genomeMapper.updateById(row);
            }
        }

        boolean ownerBound = authorizedCards.stream()
                .allMatch(card -> capsule.ownerUserId.equals(card.userId));
        boolean currentOnly = authorizedCards.stream()
                .allMatch(card -> "ACTIVE".equalsIgnoreCase(card.status));
        if (!ownerBound || !currentOnly) throw new BusinessException(
                "CAPSULE_AUTHORIZATION_INVALID", "共鸣体只能编译主人当前明确授权的记忆");

        CapsuleGenomeVersion genome = new CapsuleGenomeVersion();
        genome.capsuleId = capsule.id;
        genome.ownerUserId = capsule.ownerUserId;
        genome.versionNo = previous == null ? 1 : previous.versionNo + 1;
        genome.parentVersionId = previous == null ? null : previous.id;
        genome.compilerVersion = COMPILER_VERSION;
        genome.status = "ACTIVE";
        genome.authorizationSnapshotJson = write(Map.of(
                "schemaVersion", "capsule-authorization.v1",
                "memories", authorizedCards.stream().map(card -> Map.of(
                        "memoryId", card.id,
                        "sourceVersion", card.versionNo == null ? 1 : card.versionNo,
                        "consentScope", card.consentScope == null ? "EXPLICIT_CAPSULE_SELECTION" : card.consentScope
                )).toList()));
        genome.compiledPersonaPrompt = capsule.personaPrompt == null ? "" : capsule.personaPrompt;
        genome.styleProfileJson = capsule.styleProfileJson;
        genome.contextPreviewJson = capsule.contextPreviewJson;
        genome.evaluationJson = write(Map.of(
                "schemaVersion", "capsule-compiler-evaluation.v1",
                "ownerBound", ownerBound,
                "currentOnly", currentOnly,
                "authorizedMemoryCount", authorizedCards.size(),
                "identityDisclosureAllowed", false,
                "runtimeEligible", true));
        genome.changeReason = reason;
        genomeMapper.insert(genome);
        capsule.activeGenomeVersionId = genome.id;
        capsuleMapper.updateById(capsule);
        return genome;
    }

    @Override
    public CapsuleGenomeVersion current(Long capsuleId) {
        return genomeMapper.selectOne(new QueryWrapper<CapsuleGenomeVersion>()
                .eq("capsule_id", capsuleId).eq("status", "ACTIVE").last("LIMIT 1"));
    }

    @Override
    public List<CapsuleGenomeVersion> history(Long ownerUserId, Long capsuleId) {
        EchoCapsule capsule = capsuleMapper.selectById(capsuleId);
        if (capsule == null || !ownerUserId.equals(capsule.ownerUserId)) {
            throw new BusinessException("UNAUTHORIZED", "无权查看此共鸣体的版本历史");
        }
        return genomeMapper.selectList(new QueryWrapper<CapsuleGenomeVersion>()
                .eq("capsule_id", capsuleId).orderByDesc("version_no"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markNeedsReview(Long capsuleId, String reason) {
        transitionCurrent(capsuleId, "NEEDS_REVIEW", reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long capsuleId, String reason) {
        EchoCapsule capsule = capsuleMapper.selectById(capsuleId);
        CapsuleGenomeVersion selected = capsule == null || capsule.activeGenomeVersionId == null
                ? null : genomeMapper.selectById(capsule.activeGenomeVersionId);
        if (selected == null) {
            selected = genomeMapper.selectOne(new QueryWrapper<CapsuleGenomeVersion>()
                    .eq("capsule_id", capsuleId).orderByDesc("version_no").last("LIMIT 1"));
        }
        if (selected == null || "WITHDRAWN".equals(selected.status)) return;
        selected.status = "WITHDRAWN";
        selected.changeReason = reason;
        genomeMapper.updateById(selected);
    }

    private void transitionCurrent(Long capsuleId, String status, String reason) {
        CapsuleGenomeVersion current = current(capsuleId);
        if (current == null) return;
        current.status = status;
        current.changeReason = reason;
        genomeMapper.updateById(current);
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception impossible) { throw new IllegalStateException("Unable to serialize Capsule Genome", impossible); }
    }
}
