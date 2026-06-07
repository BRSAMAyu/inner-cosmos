package com.innercosmos.controller;

import com.innercosmos.ai.self.UserTriggeredSelfReflection;
import com.innercosmos.ai.self.dto.*;
import com.innercosmos.entity.AuroraSelfModel;
import com.innercosmos.entity.AuroraSelfStatement;
import com.innercosmos.entity.AuroraSelfReflection;
import com.innercosmos.service.AuroraConstitutionService;
import com.innercosmos.service.AuroraSelfContinuityService;
import com.innercosmos.ai.self.AuroraConstitutionVO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/aurora/self")
public class AuroraSelfController {

    private final AuroraSelfContinuityService continuityService;
    private final AuroraConstitutionService constitutionService;
    private final UserTriggeredSelfReflection selfReflection;

    public AuroraSelfController(
            AuroraSelfContinuityService continuityService,
            AuroraConstitutionService constitutionService,
            UserTriggeredSelfReflection selfReflection) {
        this.continuityService = continuityService;
        this.constitutionService = constitutionService;
        this.selfReflection = selfReflection;
    }

    @GetMapping("/constitution")
    public ResponseEntity<?> getConstitution() {
        AuroraConstitutionVO c = constitutionService.get();
        return c != null ? ResponseEntity.ok(c) : ResponseEntity.ok(java.util.Map.of("status", "not_initialized"));
    }

    @GetMapping("/statements")
    public ResponseEntity<List<SelfStatementVO>> getStatements(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "10") int limit) {
        List<AuroraSelfStatement> stmts = continuityService.getRecentStatements(userId, limit);
        List<SelfStatementVO> vos = stmts.stream().map(s -> {
            SelfStatementVO vo = new SelfStatementVO();
            vo.setId(s.id);
            vo.setUserId(s.userId);
            vo.setSessionId(s.sessionId);
            vo.setMessageId(s.messageId);
            vo.setStatementText(s.statementText);
            vo.setTrigger(s.trigger);
            vo.setCreatedAt(s.createdAt != null ? s.createdAt.toString() : null);
            return vo;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(vos);
    }

    @GetMapping("/reflections")
    public ResponseEntity<List<SelfReflectionVO>> getReflections(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "10") int limit) {
        List<AuroraSelfReflection> refs = continuityService.getRecentReflections(userId, limit);
        List<SelfReflectionVO> vos = refs.stream().map(r -> {
            SelfReflectionVO vo = new SelfReflectionVO();
            vo.setId(r.id);
            vo.setUserId(r.userId);
            vo.setTrigger(r.trigger);
            vo.setDepth(r.depth);
            vo.setSummary(r.summary);
            vo.setRelatedStatementId(r.relatedStatementId);
            vo.setDimension(r.dimension);
            vo.setProposedBelief(r.proposedBelief);
            vo.setConfidence(r.confidence);
            vo.setStatus(r.status);
            vo.setRiskFlags(r.riskFlags);
            vo.setCreatedAt(r.createdAt != null ? r.createdAt.toString() : null);
            return vo;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(vos);
    }

    @GetMapping("/model")
    public ResponseEntity<List<SelfModelVO>> getModel(@RequestParam Long userId) {
        List<AuroraSelfModel> models = continuityService.getActiveModel(userId);
        List<SelfModelVO> vos = models.stream().map(m -> {
            SelfModelVO vo = new SelfModelVO();
            vo.setId(m.id);
            vo.setUserId(m.userId);
            vo.setDimension(m.dimension);
            vo.setBelief(m.belief);
            vo.setConfidence(m.confidence);
            vo.setEvidenceRefs(m.evidenceRefs);
            vo.setStatus(m.status);
            vo.setCommittedAt(m.committedAt != null ? m.committedAt.toString() : null);
            vo.setRevisionCount(m.revisionCount);
            return vo;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(vos);
    }

    @GetMapping("/candidates")
    public ResponseEntity<List<SelfReflectionVO>> getCandidates(@RequestParam Long userId) {
        List<AuroraSelfReflection> candidates = continuityService.getCandidates(userId);
        List<SelfReflectionVO> vos = candidates.stream().map(r -> {
            SelfReflectionVO vo = new SelfReflectionVO();
            vo.setId(r.id);
            vo.setUserId(r.userId);
            vo.setTrigger(r.trigger);
            vo.setDepth(r.depth);
            vo.setSummary(r.summary);
            vo.setDimension(r.dimension);
            vo.setProposedBelief(r.proposedBelief);
            vo.setConfidence(r.confidence);
            vo.setStatus(r.status);
            vo.setRiskFlags(r.riskFlags);
            vo.setCreatedAt(r.createdAt != null ? r.createdAt.toString() : null);
            return vo;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(vos);
    }

    @PostMapping("/commit")
    public ResponseEntity<?> commitCandidate(
            @RequestParam Long userId,
            @RequestBody CommitRequest req) {
        try {
            continuityService.commitToModel(userId, req.getCandidateId(),
                req.getUserConfirmed() != null && req.getUserConfirmed(),
                req.getExtraEvidence() != null ? req.getExtraEvidence() : java.util.List.of());
            return ResponseEntity.ok(java.util.Map.of("status", "committed"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/retire")
    public ResponseEntity<?> retireModel(@RequestParam Long userId, @RequestParam Long modelId) {
        return ResponseEntity.ok(java.util.Map.of("status", "not_implemented_yet"));
    }
}