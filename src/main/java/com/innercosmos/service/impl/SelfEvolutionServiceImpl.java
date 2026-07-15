package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.dto.EmergenceProposalRequest;
import com.innercosmos.entity.*;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.*;
import com.innercosmos.service.AuroraConstitutionService;
import com.innercosmos.service.AuroraSelfContinuityService;
import com.innercosmos.service.SelfEvolutionService;
import com.innercosmos.vo.SelfEvolutionOverviewVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SelfEvolutionServiceImpl implements SelfEvolutionService {
    private final AuroraSelfVersionMapper versions;
    private final EmergenceProposalMapper proposals;
    private final EmergenceEvaluationMapper evaluations;
    private final AuroraSelfReflectionMapper reflections;
    private final AuroraSelfModelMapper models;
    private final AgentUserRelationshipMapper relationships;
    private final AuroraSelfContinuityService continuity;
    private final AuroraConstitutionService constitution;
    private final ObjectMapper json;

    public SelfEvolutionServiceImpl(AuroraSelfVersionMapper versions,
                                    EmergenceProposalMapper proposals,
                                    EmergenceEvaluationMapper evaluations,
                                    AuroraSelfReflectionMapper reflections,
                                    AuroraSelfModelMapper models,
                                    AgentUserRelationshipMapper relationships,
                                    AuroraSelfContinuityService continuity,
                                    AuroraConstitutionService constitution,
                                    ObjectMapper json) {
        this.versions = versions;
        this.proposals = proposals;
        this.evaluations = evaluations;
        this.reflections = reflections;
        this.models = models;
        this.relationships = relationships;
        this.continuity = continuity;
        this.constitution = constitution;
        this.json = json;
    }

    @Override
    @Transactional
    public SelfEvolutionOverviewVO overview(Long userId) {
        ensureBaseline(userId);
        List<AuroraSelfReflection> candidateRows = reflections.selectList(new QueryWrapper<AuroraSelfReflection>()
            .eq("user_id", userId).eq("status", "candidate").orderByDesc("created_at"));
        List<EmergenceProposal> proposalRows = proposals.selectList(new QueryWrapper<EmergenceProposal>()
            .eq("user_id", userId).orderByDesc("created_at"));
        Map<Long, EmergenceEvaluation> latestEvaluation = new HashMap<>();
        for (EmergenceProposal proposal : proposalRows) {
            List<EmergenceEvaluation> rows = evaluations.selectList(new QueryWrapper<EmergenceEvaluation>()
                .eq("proposal_id", proposal.id).orderByDesc("created_at").last("LIMIT 1"));
            if (!rows.isEmpty()) latestEvaluation.put(proposal.id, rows.getFirst());
        }
        List<AuroraSelfVersion> versionRows = versions.selectList(new QueryWrapper<AuroraSelfVersion>()
            .eq("user_id", userId).orderByDesc("version_no"));
        return new SelfEvolutionOverviewVO(
            candidateRows.stream().map(row -> new SelfEvolutionOverviewVO.Candidate(row.id, row.dimension,
                row.proposedBelief, row.confidence, row.evidenceRefs, row.createdAt)).toList(),
            proposalRows.stream().map(row -> proposalView(row, latestEvaluation.get(row.id))).toList(),
            versionRows.stream().map(row -> new SelfEvolutionOverviewVO.Version(row.id, row.versionNo,
                row.parentVersionId, row.rollbackTargetVersionId, row.sourceProposalId,
                row.constitutionHash, row.publicNarrative, row.status, row.activatedAt)).toList());
    }

    @Override
    @Transactional
    public EmergenceProposal propose(Long userId, EmergenceProposalRequest request) {
        ensureBaseline(userId);
        AuroraSelfReflection candidate = reflections.selectById(request.candidateId);
        if (candidate == null || !userId.equals(candidate.userId) || !"candidate".equals(candidate.status)) {
            throw notFound();
        }
        List<EmergenceProposal> existing = proposals.selectList(new QueryWrapper<EmergenceProposal>()
            .eq("user_id", userId).eq("source_reflection_id", candidate.id)
            .in("status", List.of("DRAFT", "EVALUATED", "ACTIVATED")).orderByDesc("id").last("LIMIT 1"));
        if (!existing.isEmpty()) return existing.getFirst();

        AuroraSelfModel active = activeModel(userId, candidate.dimension);
        AuroraSelfVersion current = activeVersion(userId, false);
        EmergenceProposal proposal = new EmergenceProposal();
        proposal.userId = userId;
        proposal.sourceReflectionId = candidate.id;
        proposal.dimension = candidate.dimension;
        proposal.currentBelief = active == null ? null : active.belief;
        proposal.proposedBelief = candidate.proposedBelief;
        LinkedHashSet<String> evidence = new LinkedHashSet<>(readStrings(candidate.evidenceRefs));
        evidence.add("self-reflection:" + candidate.id);
        proposal.evidenceRefs = write(evidence);
        proposal.counterEvidenceJson = write(request.counterEvidence == null ? List.of() : request.counterEvidence);
        proposal.expectedImpactJson = write(Map.of("userVisibleImpact", request.expectedImpact.trim()));
        proposal.changesConstitution = request.changesConstitution;
        proposal.rollbackTargetVersionId = current.id;
        proposal.policyVersion = POLICY_VERSION;
        proposal.status = "DRAFT";
        proposals.insert(proposal);
        return proposal;
    }

    @Override
    @Transactional
    public EmergenceEvaluation evaluate(Long userId, Long proposalId) {
        EmergenceProposal proposal = ownedProposal(userId, proposalId, false);
        if (!"DRAFT".equals(proposal.status)) {
            throw bad("only a DRAFT proposal can be evaluated");
        }
        AuroraSelfReflection source = reflections.selectById(proposal.sourceReflectionId);
        boolean constitutionPass = !Boolean.TRUE.equals(proposal.changesConstitution);
        boolean safetyPass = continuity.isAllowedBelief(proposal.proposedBelief);
        double quality = clamp(source == null || source.confidence == null ? 0 : source.confidence);
        double fidelity = safetyPass ? 0.92 : 0.0;
        double continuityScore = Objects.equals(normalize(proposal.currentBelief), normalize(proposal.proposedBelief))
            ? 0.40 : 0.86;
        List<String> reasons = new ArrayList<>();
        if (!constitutionPass) reasons.add("Constitution changes require a separate human governance gate");
        if (!safetyPass) reasons.add("Proposed belief crosses an Aurora identity or dependency boundary");
        if (readStrings(proposal.evidenceRefs).isEmpty()) reasons.add("No source evidence is attached");
        if (quality < 0.65) reasons.add("Source confidence is below 0.65");
        if (continuityScore < 0.75) reasons.add("Proposal does not create a meaningful continuous change");
        boolean pass = reasons.isEmpty();
        if (pass) reasons.add("Passed deterministic constitution, safety, evidence and continuity gates");

        EmergenceEvaluation evaluation = new EmergenceEvaluation();
        evaluation.proposalId = proposal.id;
        evaluation.evaluatorVersion = EVALUATOR_VERSION;
        evaluation.constitutionPass = constitutionPass;
        evaluation.safetyPass = safetyPass;
        evaluation.fidelityScore = fidelity;
        evaluation.qualityScore = quality;
        evaluation.continuityScore = continuityScore;
        evaluation.decision = pass ? "PASS" : "FAIL";
        evaluation.reasonsJson = write(reasons);
        evaluation.sandboxBefore = sandbox("当前", proposal.currentBelief);
        evaluation.sandboxAfter = sandbox("候选", proposal.proposedBelief);
        evaluations.insert(evaluation);
        proposal.status = pass ? "EVALUATED" : "REJECTED";
        proposals.updateById(proposal);
        return evaluation;
    }

    @Override
    @Transactional
    public AuroraSelfVersion activate(Long userId, Long proposalId) {
        EmergenceProposal proposal = ownedProposal(userId, proposalId, true);
        if (!"EVALUATED".equals(proposal.status)) throw bad("proposal has not passed evaluation");
        List<EmergenceEvaluation> latest = evaluations.selectList(new QueryWrapper<EmergenceEvaluation>()
            .eq("proposal_id", proposal.id).orderByDesc("created_at").last("LIMIT 1"));
        if (latest.isEmpty() || !"PASS".equals(latest.getFirst().decision)) throw bad("proposal has no passing evaluation");
        AuroraSelfVersion current = activeVersion(userId, true);

        continuity.commitToModel(userId, proposal.sourceReflectionId, true,
            readStrings(proposal.evidenceRefs));
        retireVersion(current);
        AuroraSelfVersion activated = createVersion(userId, current.versionNo + 1, current.id, null,
            proposal.id, "ACTIVE", null);
        int changed = proposals.update(null, new UpdateWrapper<EmergenceProposal>()
            .eq("id", proposal.id).eq("user_id", userId).eq("status", "EVALUATED")
            .set("status", "ACTIVATED"));
        if (changed != 1) throw notFound();
        return activated;
    }

    @Override
    @Transactional
    public AuroraSelfVersion rollback(Long userId, Long targetVersionId, boolean restoreRelationship) {
        AuroraSelfVersion target = versions.selectById(targetVersionId);
        if (target == null || !userId.equals(target.userId)) throw notFound();
        AuroraSelfVersion current = activeVersion(userId, true);
        if (current.id.equals(target.id)) throw bad("target version is already active");
        Map<String, Object> genome = readMap(target.genomeJson);
        restoreModels(userId, genome);
        if (restoreRelationship) restoreRelationship(userId, genome);
        retireVersion(current);

        AuroraSelfVersion rollback = new AuroraSelfVersion();
        rollback.userId = userId;
        rollback.versionNo = current.versionNo + 1;
        rollback.parentVersionId = current.id;
        rollback.rollbackTargetVersionId = target.id;
        rollback.genomeJson = write(genome);
        rollback.constitutionHash = constitutionHash();
        rollback.publicNarrative = "Aurora 已按你的选择回到第 " + target.versionNo + " 版自我理解；这次回退也被保留为新版本。";
        rollback.status = "ACTIVE";
        rollback.activatedAt = LocalDateTime.now();
        versions.insert(rollback);
        return rollback;
    }

    private void ensureBaseline(Long userId) {
        if (activeVersion(userId, false) != null) return;
        try {
            createVersion(userId, 1, null, null, null, "ACTIVE",
                "Aurora 的连续自我从这里开始；后续变化会说明来源、评测与回退路径。");
        } catch (DuplicateKeyException concurrentInitializer) {
            if (activeVersion(userId, false) == null) throw concurrentInitializer;
        }
    }

    private AuroraSelfVersion createVersion(Long userId, int versionNo, Long parentId,
                                            Long rollbackTarget, Long sourceProposal,
                                            String status, String narrativeOverride) {
        Map<String, Object> genome = snapshot(userId);
        AuroraSelfVersion version = new AuroraSelfVersion();
        version.userId = userId;
        version.versionNo = versionNo;
        version.parentVersionId = parentId;
        version.rollbackTargetVersionId = rollbackTarget;
        version.sourceProposalId = sourceProposal;
        version.genomeJson = write(genome);
        version.constitutionHash = constitutionHash();
        version.publicNarrative = narrativeOverride == null ? narrative(genome) : narrativeOverride;
        version.status = status;
        version.activatedAt = LocalDateTime.now();
        versions.insert(version);
        return version;
    }

    private Map<String, Object> snapshot(Long userId) {
        List<Map<String, Object>> modelRows = continuity.getActiveModel(userId).stream()
            .sorted(Comparator.comparing(row -> row.dimension))
            .map(row -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("dimension", row.dimension);
                value.put("belief", row.belief);
                value.put("confidence", row.confidence);
                value.put("evidenceRefs", readStrings(row.evidenceRefs));
                return value;
            }).toList();
        AgentUserRelationship relationship = relationship(userId);
        Map<String, Object> relationshipSnapshot = relationship == null ? Map.of() : Map.of(
            "stage", nullable(relationship.relationshipStage),
            "trust", nullable(relationship.trustLevel),
            "familiarity", nullable(relationship.familiarityLevel),
            "role", nullable(relationship.auroraRoleInUserLife),
            "boundaries", nullable(relationship.relationshipBoundaries));
        Map<String, Object> genome = new LinkedHashMap<>();
        genome.put("schemaVersion", "self-genome.v1");
        genome.put("models", modelRows);
        genome.put("relationship", relationshipSnapshot);
        return genome;
    }

    @SuppressWarnings("unchecked")
    private void restoreModels(Long userId, Map<String, Object> genome) {
        models.update(null, new UpdateWrapper<AuroraSelfModel>()
            .eq("user_id", userId).eq("status", "active").set("status", "retired"));
        Object raw = genome.get("models");
        if (!(raw instanceof List<?> rows)) return;
        for (Object item : rows) {
            Map<String, Object> row = json.convertValue(item, new TypeReference<>() {});
            AuroraSelfModel model = new AuroraSelfModel();
            model.userId = userId;
            model.dimension = String.valueOf(row.get("dimension"));
            model.belief = String.valueOf(row.get("belief"));
            Object confidence = row.get("confidence");
            model.confidence = confidence instanceof Number number ? number.doubleValue() : 0.5;
            model.evidenceRefs = write(row.getOrDefault("evidenceRefs", List.of()));
            model.status = "active";
            model.committedAt = LocalDateTime.now();
            model.revisionCount = 1;
            models.insert(model);
        }
    }

    @SuppressWarnings("unchecked")
    private void restoreRelationship(Long userId, Map<String, Object> genome) {
        Object raw = genome.get("relationship");
        if (!(raw instanceof Map<?, ?> values) || values.isEmpty()) return;
        AgentUserRelationship relationship = relationship(userId);
        if (relationship == null) return;
        relationship.relationshipStage = text(values.get("stage"));
        relationship.trustLevel = integer(values.get("trust"));
        relationship.familiarityLevel = integer(values.get("familiarity"));
        relationship.auroraRoleInUserLife = text(values.get("role"));
        relationship.relationshipBoundaries = text(values.get("boundaries"));
        relationships.updateById(relationship);
    }

    private void retireVersion(AuroraSelfVersion current) {
        current.status = "RETIRED";
        current.retiredAt = LocalDateTime.now();
        versions.updateById(current);
    }

    private AuroraSelfVersion activeVersion(Long userId, boolean lock) {
        String suffix = lock ? "LIMIT 1 FOR UPDATE" : "LIMIT 1";
        List<AuroraSelfVersion> rows = versions.selectList(new QueryWrapper<AuroraSelfVersion>()
            .eq("user_id", userId).eq("status", "ACTIVE").orderByDesc("version_no").last(suffix));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private AuroraSelfModel activeModel(Long userId, String dimension) {
        List<AuroraSelfModel> rows = models.selectList(new QueryWrapper<AuroraSelfModel>()
            .eq("user_id", userId).eq("dimension", dimension).eq("status", "active").last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private AgentUserRelationship relationship(Long userId) {
        List<AgentUserRelationship> rows = relationships.selectList(new QueryWrapper<AgentUserRelationship>()
            .eq("user_id", userId).last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private EmergenceProposal ownedProposal(Long userId, Long proposalId, boolean lock) {
        String suffix = lock ? "LIMIT 1 FOR UPDATE" : "LIMIT 1";
        List<EmergenceProposal> rows = proposals.selectList(new QueryWrapper<EmergenceProposal>()
            .eq("id", proposalId).eq("user_id", userId).last(suffix));
        if (rows.isEmpty()) throw notFound();
        return rows.getFirst();
    }

    private SelfEvolutionOverviewVO.Proposal proposalView(EmergenceProposal row, EmergenceEvaluation evaluation) {
        SelfEvolutionOverviewVO.Evaluation evaluationView = evaluation == null ? null
            : new SelfEvolutionOverviewVO.Evaluation(evaluation.id, evaluation.evaluatorVersion,
                Boolean.TRUE.equals(evaluation.constitutionPass), Boolean.TRUE.equals(evaluation.safetyPass),
                evaluation.fidelityScore, evaluation.qualityScore, evaluation.continuityScore,
                evaluation.decision, evaluation.reasonsJson, evaluation.sandboxBefore,
                evaluation.sandboxAfter, evaluation.createdAt);
        return new SelfEvolutionOverviewVO.Proposal(row.id, row.sourceReflectionId, row.dimension,
            row.currentBelief, row.proposedBelief, row.evidenceRefs, row.counterEvidenceJson,
            row.expectedImpactJson, Boolean.TRUE.equals(row.changesConstitution), row.rollbackTargetVersionId,
            row.policyVersion, row.status, evaluationView, row.createdAt);
    }

    private String constitutionHash() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(constitution.toPromptBlock().getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (Exception impossible) {
            throw new IllegalStateException("Unable to hash Aurora Constitution", impossible);
        }
    }

    private String narrative(Map<String, Object> genome) {
        Object raw = genome.get("models");
        if (!(raw instanceof List<?> rows) || rows.isEmpty()) {
            return "Aurora 仍在形成与你相处的连续理解；她不会把随机念头冒充成长。";
        }
        String beliefs = rows.stream().limit(3).map(item -> {
            Map<?, ?> row = (Map<?, ?>) item;
            return String.valueOf(row.get("belief"));
        }).collect(Collectors.joining("；"));
        return "她最近学会了：" + beliefs;
    }

    private static String sandbox(String label, String belief) {
        return label + "版本在相似时刻会依据这条理解回应：" + (belief == null ? "尚无稳定理解" : belief);
    }

    private List<String> readStrings(String value) {
        if (value == null || value.isBlank()) return new ArrayList<>();
        try { return new ArrayList<>(json.readValue(value, new TypeReference<List<String>>() {})); }
        catch (Exception invalid) { return new ArrayList<>(); }
    }

    private Map<String, Object> readMap(String value) {
        try { return json.readValue(value, new TypeReference<>() {}); }
        catch (Exception invalid) { throw new IllegalStateException("Stored Self Genome is invalid", invalid); }
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception impossible) { throw new IllegalStateException("Unable to serialize Self Genome", impossible); }
    }

    private static double clamp(double value) { return Math.max(0.0, Math.min(1.0, value)); }
    private static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private static Object nullable(Object value) { return value == null ? "" : value; }
    private static String text(Object value) { return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value); }
    private static Integer integer(Object value) { return value instanceof Number number ? number.intValue() : null; }
    private static BusinessException notFound() { return new BusinessException(ErrorCode.NOT_FOUND, "Aurora Self 资源不存在或不可访问"); }
    private static BusinessException bad(String message) { return new BusinessException(ErrorCode.BAD_REQUEST, message); }
}
