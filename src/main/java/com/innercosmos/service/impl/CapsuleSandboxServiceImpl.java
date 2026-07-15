package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.dto.CapsuleSandboxFeedbackRequest;
import com.innercosmos.entity.CapsuleGenomeVersion;
import com.innercosmos.entity.CapsuleSandboxFeedback;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.CapsuleGenomeVersionMapper;
import com.innercosmos.mapper.CapsuleSandboxFeedbackMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.service.CapsuleSandboxService;
import com.innercosmos.service.SafetyService;
import com.innercosmos.vo.CapsuleSandboxVO;
import com.innercosmos.vo.SafetyResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CapsuleSandboxServiceImpl implements CapsuleSandboxService {
    private static final Set<String> RATINGS = Set.of(
            "LIKE_ME", "NOT_ME", "FACT_WRONG", "TOO_EXPOSED", "TONE_WRONG");
    private final EchoCapsuleMapper capsuleMapper;
    private final CapsuleGenomeVersionMapper genomeMapper;
    private final CapsuleSandboxFeedbackMapper feedbackMapper;
    private final StructuredAiService structuredAiService;
    private final SafetyService safetyService;

    public CapsuleSandboxServiceImpl(EchoCapsuleMapper capsuleMapper,
                                     CapsuleGenomeVersionMapper genomeMapper,
                                     CapsuleSandboxFeedbackMapper feedbackMapper,
                                     StructuredAiService structuredAiService,
                                     SafetyService safetyService) {
        this.capsuleMapper = capsuleMapper;
        this.genomeMapper = genomeMapper;
        this.feedbackMapper = feedbackMapper;
        this.structuredAiService = structuredAiService;
        this.safetyService = safetyService;
    }

    @Override
    public CapsuleSandboxVO respond(Long ownerUserId, Long capsuleId, String question) {
        EchoCapsule capsule = owned(ownerUserId, capsuleId);
        CapsuleGenomeVersion genome = selected(capsule);
        SafetyResult safety = safetyService.check(question, ownerUserId, null);
        if (Boolean.TRUE.equals(safety.blockModelCall)) {
            return new CapsuleSandboxVO(capsule.id, genome.id, genome.versionNo, genome.status,
                    question, safety.safeMessage, "安全边界已优先处理", List.of("SAFETY_BLOCKED"),
                    false, "仅你可见的共鸣体沙盒，不会发送给其他人。");
        }

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("personaPrompt", genome.compiledPersonaPrompt == null ? "" : genome.compiledPersonaPrompt);
        context.put("styleProfile", genome.styleProfileJson == null ? "" : genome.styleProfileJson);
        context.put("contextPreview", genome.contextPreviewJson == null ? "" : genome.contextPreviewJson);
        context.put("question", question);
        context.put("genomeVersion", genome.versionNo);
        StructuredAiResults.PersonaResult result = structuredAiService.call(ownerUserId, "CAPSULE_SANDBOX",
                """
                只返回 JSON：{"reply":"","boundaryNotice":"","letterSuggested":false,"riskFlags":[]}
                这是共鸣体主人自己的隔离沙盒，不是公开对话，也不能冒充真人正在在线。
                依据 personaPrompt、styleProfile 与 contextPreview 回答 question，保持具体语言风格和判断方式。
                不得补写未提供的事实、真实身份或联系方式；触及未知事实时自然说明这个侧面没有被授权。
                不要把沙盒回答发送给任何访客，也不要因为这轮对话直接改变 Genome。
                """, context, StructuredAiResults.PersonaResult.class, CapsuleSandboxServiceImpl::unavailable);
        List<String> riskFlags = result.riskFlags == null ? List.of() : result.riskFlags;
        boolean available = !riskFlags.contains("REMOTE_UNAVAILABLE")
                && result.reply != null && !result.reply.isBlank();
        return new CapsuleSandboxVO(capsule.id, genome.id, genome.versionNo, genome.status,
                question, result.reply, result.boundaryNotice, riskFlags, available,
                "仅你可见的共鸣体沙盒，不会发送给其他人。");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CapsuleSandboxFeedback recordFeedback(Long ownerUserId, Long capsuleId,
                                                   CapsuleSandboxFeedbackRequest request) {
        EchoCapsule capsule = owned(ownerUserId, capsuleId);
        CapsuleGenomeVersion genome = genomeMapper.selectById(request.genomeVersionId);
        if (genome == null || !capsule.id.equals(genome.capsuleId)
                || !ownerUserId.equals(genome.ownerUserId)) {
            throw new BusinessException("CAPSULE_VERSION_NOT_FOUND", "找不到这个共鸣体版本");
        }
        if (!RATINGS.contains(request.rating)) {
            throw new BusinessException("BAD_REQUEST", "不支持的沙盒反馈类型");
        }
        CapsuleSandboxFeedback feedback = new CapsuleSandboxFeedback();
        feedback.capsuleId = capsuleId;
        feedback.genomeVersionId = genome.id;
        feedback.ownerUserId = ownerUserId;
        feedback.question = request.question.trim();
        feedback.responseText = request.response.trim();
        feedback.rating = request.rating;
        feedback.ownerComment = request.comment == null ? null : request.comment.trim();
        feedback.status = "OPEN";
        feedbackMapper.insert(feedback);
        return feedback;
    }

    @Override
    public List<CapsuleSandboxFeedback> feedback(Long ownerUserId, Long capsuleId) {
        owned(ownerUserId, capsuleId);
        return feedbackMapper.selectList(new QueryWrapper<CapsuleSandboxFeedback>()
                .eq("capsule_id", capsuleId).orderByDesc("id"));
    }

    private EchoCapsule owned(Long ownerUserId, Long capsuleId) {
        EchoCapsule capsule = capsuleMapper.selectById(capsuleId);
        if (capsule == null || !ownerUserId.equals(capsule.ownerUserId)) {
            throw new BusinessException("UNAUTHORIZED", "无权使用此共鸣体沙盒");
        }
        return capsule;
    }

    private CapsuleGenomeVersion selected(EchoCapsule capsule) {
        CapsuleGenomeVersion genome = capsule.activeGenomeVersionId == null
                ? null : genomeMapper.selectById(capsule.activeGenomeVersionId);
        if (genome == null || "WITHDRAWN".equals(genome.status)) {
            throw new BusinessException("CAPSULE_VERSION_NOT_FOUND", "这个共鸣体没有可试聊的版本");
        }
        return genome;
    }

    private static StructuredAiResults.PersonaResult unavailable() {
        StructuredAiResults.PersonaResult result = new StructuredAiResults.PersonaResult();
        result.reply = "真实模型暂时不可用。这次沙盒不会用模板冒充你的共鸣体。";
        result.boundaryNotice = "未生成可评估回应";
        result.letterSuggested = false;
        result.riskFlags = List.of("REMOTE_UNAVAILABLE");
        return result;
    }
}
