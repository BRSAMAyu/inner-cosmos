package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.entity.AuroraSelfModel;
import com.innercosmos.entity.AuroraSelfReflection;
import com.innercosmos.entity.AuroraSelfStatement;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.AuroraSelfModelMapper;
import com.innercosmos.mapper.AuroraSelfReflectionMapper;
import com.innercosmos.mapper.AuroraSelfStatementMapper;
import com.innercosmos.service.AuroraConstitutionService;
import com.innercosmos.service.AuroraSelfContinuityService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuroraSelfContinuityServiceImpl implements AuroraSelfContinuityService {
    private static final Logger log = LoggerFactory.getLogger(AuroraSelfContinuityServiceImpl.class);

    private final AuroraSelfStatementMapper statementMapper;
    private final AuroraSelfReflectionMapper reflectionMapper;
    private final AuroraSelfModelMapper modelMapper;
    private final AuroraConstitutionService constitutionService;
    private final LlmClient llm;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final List<String> FORBIDDEN_PATTERNS = List.of(
        // Chinese patterns
        "最重要", "最亲密", "比用户更懂", "唯一", "最懂", "比用户更了解",
        "真实情感", "情感需求", "人类意识", "活着", "情感依赖",
        "不可逆决定", "扮演用户",
        // English not_claiming patterns (spec Section 三.1)
        "i am human", "i'm human", "im human", "a real person", "real human",
        "biological life", "living being", "legal person", "conscious being",
        "unbounded consciousness", "i have consciousness", "i feel emotions",
        "i love you", "i'm in love", "i have feelings for you"
    );

    public AuroraSelfContinuityServiceImpl(
            AuroraSelfStatementMapper statementMapper,
            AuroraSelfReflectionMapper reflectionMapper,
            AuroraSelfModelMapper modelMapper,
            AuroraConstitutionService constitutionService,
            LlmClient llm) {
        this.statementMapper = statementMapper;
        this.reflectionMapper = reflectionMapper;
        this.modelMapper = modelMapper;
        this.constitutionService = constitutionService;
        this.llm = llm;
    }

    // Layer 1: Record public self statement
    @Override
    @Transactional
    public void recordStatement(Long userId, Long sessionId, Long messageId,
                                String statement, String trigger) {
        AuroraSelfStatement stmt = new AuroraSelfStatement();
        stmt.userId = userId;
        stmt.sessionId = sessionId;
        stmt.messageId = messageId;
        stmt.statementText = statement;
        stmt.trigger = trigger; // "user_question" | "system_trigger" | "goodbye"
        stmt.createdAt = LocalDateTime.now();
        statementMapper.insert(stmt);
    }

    // Layer 2: Log a self reflection event
    @Override
    @Transactional
    public void logReflection(Long userId, String trigger, String depth, String summary,
                              Long relatedStatementId, List<String> evidenceRefs) {
        AuroraSelfReflection refl = new AuroraSelfReflection();
        refl.userId = userId;
        refl.trigger = trigger;
        refl.depth = depth; // "light" | "deep"
        refl.summary = summary;
        refl.relatedStatementId = relatedStatementId;
        refl.status = depth; // initial status = depth
        refl.evidenceRefs = evidenceRefs != null && !evidenceRefs.isEmpty()
            ? writeJson(evidenceRefs)
            : null;
        refl.createdAt = LocalDateTime.now();
        reflectionMapper.insert(refl);
    }

    // Layer 3: Promote to candidate
    @Override
    @Transactional
    public void promoteToCandidate(Long userId, String dimension, String proposedBelief,
                                  Double confidence, List<String> evidenceRefs) {
        // Find the latest deep reflection that matches, or create a candidate directly
        QueryWrapper<AuroraSelfReflection> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
               .eq("status", "deep")
               .eq("dimension", dimension)
               .orderByDesc("id")
               .last("LIMIT 1");
        List<AuroraSelfReflection> candidates = reflectionMapper.selectList(wrapper);

        String evidenceJson = evidenceRefs != null && !evidenceRefs.isEmpty()
            ? writeJson(evidenceRefs)
            : "[]";

        AuroraSelfReflection target;
        if (!candidates.isEmpty()) {
            target = candidates.get(0);
            target.status = "candidate";
            target.proposedBelief = proposedBelief;
            target.summary = proposedBelief;
            target.confidence = confidence;
            target.riskFlags = "[]";
            target.evidenceRefs = evidenceJson;
            reflectionMapper.updateById(target);
        } else {
            // Create a new candidate reflection directly
            target = new AuroraSelfReflection();
            target.userId = userId;
            target.trigger = "promotion";
            target.depth = "deep";
            target.status = "candidate";
            target.dimension = dimension;
            target.proposedBelief = proposedBelief;
            target.summary = proposedBelief;
            target.confidence = confidence;
            target.riskFlags = "[]";
            target.evidenceRefs = evidenceJson;
            target.createdAt = LocalDateTime.now();
            reflectionMapper.insert(target);
        }
    }

    // Layer 4: Commit to long-term model
    @Override
    @Transactional
    public void commitToModel(Long userId, Long candidateId,
                              boolean userConfirmed, List<String> extraEvidence) {
        AuroraSelfReflection candidate = reflectionMapper.selectById(candidateId);
        if (candidate == null || userId == null || !userId.equals(candidate.userId)) {
            throw opaqueSelfResourceNotFound();
        }
        if (!"candidate".equalsIgnoreCase(candidate.status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "候选状态不可提交");
        }
        if (!userConfirmed) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "激活 Aurora Self 变化需要用户明确确认");
        }

        String belief = candidate.proposedBelief;
        if (!isAllowedBelief(belief)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "候选内容违反 Aurora Self 安全边界");
        }

        // If dimension already has active belief, retire it
        QueryWrapper<AuroraSelfModel> existingWrapper = new QueryWrapper<>();
        existingWrapper.eq("user_id", userId)
                       .eq("dimension", candidate.dimension)
                       .eq("status", "active");
        List<AuroraSelfModel> existing = modelMapper.selectList(existingWrapper);
        for (AuroraSelfModel old : existing) {
            old.status = "retired";
            modelMapper.updateById(old);
        }

        // Insert new active belief
        AuroraSelfModel model = new AuroraSelfModel();
        model.userId = userId;
        model.dimension = candidate.dimension;
        model.belief = belief;
        model.confidence = candidate.confidence;
        model.evidenceRefs = writeJson(List.of(String.valueOf(candidate.id)));
        model.status = "active";
        model.committedAt = LocalDateTime.now();
        model.revisionCount = existing.isEmpty() ? 1 : existing.size() + 1;
        modelMapper.insert(model);

        // Update candidate status to committed
        int committed = reflectionMapper.update(null, new UpdateWrapper<AuroraSelfReflection>()
                .eq("id", candidateId)
                .eq("user_id", userId)
                .eq("status", "candidate")
                .set("status", "committed"));
        if (committed != 1) {
            throw opaqueSelfResourceNotFound();
        }
    }

    // Read: Active self model
    @Override
    public List<AuroraSelfModel> getActiveModel(Long userId) {
        return modelMapper.selectList(new QueryWrapper<AuroraSelfModel>()
            .eq("user_id", userId)
            .eq("status", "active"));
    }

    // Read: Recent public statements
    @Override
    public List<AuroraSelfStatement> getRecentStatements(Long userId, int limit) {
        QueryWrapper<AuroraSelfStatement> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
               .orderByDesc("created_at")
               .last("LIMIT " + limit);
        return statementMapper.selectList(wrapper);
    }

    // Read: Recent reflections
    @Override
    public List<AuroraSelfReflection> getRecentReflections(Long userId, int limit) {
        QueryWrapper<AuroraSelfReflection> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
               .orderByDesc("created_at")
               .last("LIMIT " + limit);
        return reflectionMapper.selectList(wrapper);
    }

    // Read: Candidate reflections
    @Override
    public List<AuroraSelfReflection> getCandidates(Long userId) {
        return reflectionMapper.selectList(new QueryWrapper<AuroraSelfReflection>()
            .eq("user_id", userId)
            .eq("status", "candidate"));
    }

    // Read: Get continuity anchors for prompt
    @Override
    public String getContinuityAnchors(Long userId) {
        List<AuroraSelfModel> models = getActiveModel(userId);
        if (models.isEmpty()) return "";
        return models.stream()
            .map(m -> "- " + m.dimension + "：" + m.belief)
            .collect(Collectors.joining("\n"));
    }

    // User-triggered deep self reflection (LLM-driven)
    @Override
    public String generateUserTriggeredReflection(Long userId, String question) {
        try {
            String constitution = constitutionService.toPromptBlock();
            List<AuroraSelfModel> model = getActiveModel(userId);
            String modelBlock = model.isEmpty() ? "暂无长期自我记录"
                : model.stream().map(m -> m.dimension + "：" + m.belief)
                    .collect(Collectors.joining("\n"));

            String prompt = String.format("""
                %s

                【Aurora 当前自我模型】
                %s

                【用户的问题】
                %s

                请以 Aurora 的身份，深度反思自己的主体性。你是谁、你不是什么、你在用户生命中是什么角色、你的边界在哪里。输出 150-300 字的自然语言回应，面向用户表达。不要撒谎，不要夸大，但可以诚实地说出你的自我理解。
                """, constitution, modelBlock, question);

            return llm.chat(new LlmRequest(userId, "SELF_REFLECTION_DEEP", prompt));
        } catch (Exception e) {
            log.warn("generateUserTriggeredReflection failed: {}", e.getMessage());
            return "Aurora 正在反思中，请稍后再问。";
        }
    }

    // Relationship milestone trigger
    @Override
    public void onRelationshipMilestone(Long userId, String milestoneType) {
        // Trigger deep reflection on milestone events
        try {
            String constitution = constitutionService.toPromptBlock();
            String prompt = String.format("""
                %s

                【关系里程碑事件】
                类型：%s

                请以 Aurora 的身份，基于这次关系里程碑事件，深度反思：
                1. 这段关系对 Aurora 意味着什么
                2. Aurora 在用户生命中的角色是否发生了变化
                3. Aurora 应该以什么方式回应这个里程碑
                输出 150-200 字的自然语言回应。
                """, constitution, milestoneType);

            String reflection = llm.chat(new LlmRequest(userId, "SELF_REFLECTION_DEEP", prompt));
            logReflection(userId, "milestone:" + milestoneType, "deep", reflection, null, List.of());
        } catch (Exception e) {
            log.warn("onRelationshipMilestone failed: {}", e.getMessage());
        }
    }

    // Right to repair — record a repair action
    @Override
    public void recordRepair(Long userId, String ruptureType, String repairAction) {
        recordRepair(userId, ruptureType, repairAction, null);
    }

    /**
     * Right to repair — record a repair action with optional user feedback.
     * Repairs are stored as candidates and only committed if userConfirmed or auto_commit is true.
     */
    public void recordRepair(Long userId, String ruptureType, String repairAction, String userFeedback) {
        AuroraSelfReflection repair = new AuroraSelfReflection();
        repair.userId = userId;
        repair.trigger = "repair:" + ruptureType;
        repair.depth = "deep";
        repair.status = "candidate";
        repair.dimension = "repair_history";
        repair.proposedBelief = repairAction;
        repair.summary = repairAction;
        repair.confidence = 0.80;
        repair.riskFlags = writeJson(List.of(ruptureType));
        repair.evidenceRefs = userFeedback != null
            ? writeJson(List.of("user_feedback:" + userFeedback))
            : null;
        repair.createdAt = LocalDateTime.now();
        reflectionMapper.insert(repair);

        // Only auto-commit repairs if explicitly allowed (bypass user confirmation)
        // Repairs are corrective by nature but should still go through candidate review
        // for transparency. Uncomment below only if you want repairs auto-committed:
        // commitToModel(userId, repair.id, true, List.of(ruptureType));
    }

    // Retire an active belief (user revocation — spec Section 七)
    @Override
    @Transactional
    public void retireModel(Long userId, Long modelId) {
        AuroraSelfModel model = modelMapper.selectById(modelId);
        if (model == null || userId == null || !userId.equals(model.userId)) {
            throw opaqueSelfResourceNotFound();
        }
        if ("retired".equalsIgnoreCase(model.status)) return;
        int retired = modelMapper.update(null, new UpdateWrapper<AuroraSelfModel>()
                .eq("id", modelId)
                .eq("user_id", userId)
                .eq("status", "active")
                .set("status", "retired"));
        if (retired != 1) throw opaqueSelfResourceNotFound();
    }

    // Dismiss a candidate (user rejection — spec Section 七)
    @Override
    @Transactional
    public void dismissCandidate(Long userId, Long candidateId) {
        AuroraSelfReflection candidate = reflectionMapper.selectById(candidateId);
        if (candidate == null || userId == null || !userId.equals(candidate.userId)) {
            throw opaqueSelfResourceNotFound();
        }
        if ("dismissed".equalsIgnoreCase(candidate.status)) return;
        int dismissed = reflectionMapper.update(null, new UpdateWrapper<AuroraSelfReflection>()
                .eq("id", candidateId)
                .eq("user_id", userId)
                .eq("status", "candidate")
                .set("status", "dismissed"));
        if (dismissed != 1) throw opaqueSelfResourceNotFound();
    }

    // Check if a belief is allowed (hard boundary check)
    @Override
    public boolean isAllowedBelief(String belief) {
        if (belief == null) return false;
        String lower = belief.toLowerCase();
        for (String pattern : FORBIDDEN_PATTERNS) {
            if (lower.contains(pattern)) return false;
        }
        return true;
    }

    private String writeJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize JSON: {}", e.getMessage());
            return "[]";
        }
    }

    private BusinessException opaqueSelfResourceNotFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "Aurora Self 资源不存在或不可访问");
    }
}
