package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.agent.CapsuleAgent;
import com.innercosmos.dto.CapsuleCreateRequest;
import com.innercosmos.entity.CapsuleBoundary;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.mapper.CapsuleBoundaryMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.service.CapsuleService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CapsuleServiceImpl implements CapsuleService {
    private final EchoCapsuleMapper capsuleMapper;
    private final CapsuleBoundaryMapper boundaryMapper;
    private final CapsuleAgent capsuleAgent;

    public CapsuleServiceImpl(EchoCapsuleMapper capsuleMapper, CapsuleBoundaryMapper boundaryMapper, CapsuleAgent capsuleAgent) {
        this.capsuleMapper = capsuleMapper;
        this.boundaryMapper = boundaryMapper;
        this.capsuleAgent = capsuleAgent;
    }

    @Override
    public EchoCapsule createFromMemory(Long userId, CapsuleCreateRequest request) {
        EchoCapsule capsule = new EchoCapsule();
        capsule.ownerUserId = userId;
        capsule.capsuleType = "USER_CAPSULE";
        capsule.pseudonym = request.pseudonym == null || request.pseudonym.isBlank() ? "未命名回声" : request.pseudonym;
        capsule.intro = request.intro == null ? "一枚从脱敏记忆中编织出的数字回声。" : request.intro;
        capsule.personaPrompt = capsuleAgent.buildPersonaPrompt(capsule.pseudonym, capsule.intro);
        capsule.publicTags = toJsonArray(request.publicTags, "self-resonance");
        capsule.authorizedMemoryIds = toJsonArray(request.memoryIds != null ? request.memoryIds.stream().map(String::valueOf).toList() : null);
        capsule.echoEnergy = 0.72;
        capsule.freshnessScore = 0.86;
        capsule.conversationLimitPerDay = safeTurns(request.maxConversationTurns);
        capsule.visibilityStatus = safeVisibility(request.visibilityStatus);
        capsule.isPublic = request.isPublic == null ? !"PRIVATE".equals(capsule.visibilityStatus) : request.isPublic;
        capsule.lastMemoryUpdateAt = LocalDateTime.now();
        capsuleMapper.insert(capsule);

        CapsuleBoundary boundary = new CapsuleBoundary();
        boundary.capsuleId = capsule.id;
        boundary.allowTopics = toJsonArray(request.allowTopics, "自我观察", "温柔建议", "日常支持");
        boundary.blockedTopics = toJsonArray(request.blockedTopics, "隐私身份", "诊断承诺", "强迫即时回应");
        boundary.maxConversationTurns = safeTurns(request.maxConversationTurns);
        boundary.allowLetterRequest = request.allowLetterRequest == null ? true : request.allowLetterRequest;
        boundary.privacyLevel = safePrivacy(request.privacyLevel);
        boundaryMapper.insert(boundary);
        return capsule;
    }

    @Override
    public EchoCapsule getOwnedCapsule(Long userId, Long capsuleId) {
        QueryWrapper<EchoCapsule> query = new QueryWrapper<>();
        query.eq("id", capsuleId).eq("owner_user_id", userId).last("LIMIT 1");
        return capsuleMapper.selectOne(query);
    }

    @Override
    public EchoCapsule updateVisibility(Long userId, Long capsuleId, String visibilityStatus, Boolean isPublic) {
        EchoCapsule capsule = getOwnedCapsule(userId, capsuleId);
        if (capsule == null) {
            return null;
        }
        capsule.visibilityStatus = safeVisibility(visibilityStatus);
        capsule.isPublic = isPublic == null ? !"PRIVATE".equals(capsule.visibilityStatus) : isPublic;
        capsuleMapper.updateById(capsule);
        return capsule;
    }

    @Override
    public List<EchoCapsule> myCapsules(Long userId) {
        QueryWrapper<EchoCapsule> query = new QueryWrapper<>();
        query.eq("owner_user_id", userId).orderByDesc("id");
        return capsuleMapper.selectList(query);
    }

    @Override
    public List<EchoCapsule> plazaCapsules() {
        QueryWrapper<EchoCapsule> query = new QueryWrapper<>();
        query.eq("is_public", true).eq("visibility_status", "PUBLIC").orderByDesc("echo_energy");
        return capsuleMapper.selectList(query);
    }

    @Override
    public CapsuleBoundary getBoundary(Long capsuleId) {
        QueryWrapper<CapsuleBoundary> query = new QueryWrapper<>();
        query.eq("capsule_id", capsuleId).last("LIMIT 1");
        return boundaryMapper.selectOne(query);
    }

    @Override
    public void updateBoundary(Long userId, Long capsuleId, CapsuleBoundary boundary) {
        EchoCapsule capsule = getOwnedCapsule(userId, capsuleId);
        if (capsule == null) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.UNAUTHORIZED, "无权操作此共鸣体");
        }
        CapsuleBoundary existing = getBoundary(capsuleId);
        if (existing == null) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.NOT_FOUND, "边界配置不存在");
        }
        if (boundary.allowTopics != null) existing.allowTopics = boundary.allowTopics;
        if (boundary.blockedTopics != null) existing.blockedTopics = boundary.blockedTopics;
        if (boundary.maxConversationTurns != null) existing.maxConversationTurns = boundary.maxConversationTurns;
        if (boundary.allowLetterRequest != null) existing.allowLetterRequest = boundary.allowLetterRequest;
        if (boundary.privacyLevel != null) existing.privacyLevel = boundary.privacyLevel;
        boundaryMapper.updateById(existing);
    }

    @Override
    public void archiveCapsule(Long userId, Long capsuleId) {
        EchoCapsule capsule = getOwnedCapsule(userId, capsuleId);
        if (capsule == null) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.UNAUTHORIZED, "无权操作此共鸣体");
        }
        capsule.visibilityStatus = "ARCHIVED";
        capsule.isPublic = false;
        capsuleMapper.updateById(capsule);
    }

    private Integer safeTurns(Integer turns) {
        if (turns == null) return 5;
        return Math.max(2, Math.min(12, turns));
    }

    private String safeVisibility(String value) {
        if ("PRIVATE".equals(value) || "HIDDEN".equals(value) || "ARCHIVED".equals(value)) {
            return value;
        }
        return "PUBLIC";
    }

    private String safePrivacy(String value) {
        if ("STRICT".equals(value) || "OPEN".equals(value)) {
            return value;
        }
        return "BALANCED";
    }

    private String toJsonArray(List<String> values, String... defaults) {
        List<String> source = values == null || values.isEmpty() ? List.of(defaults) : values;
        return source.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(v -> "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .reduce("[", (a, b) -> "[".equals(a) ? a + b : a + "," + b) + "]";
    }
}
