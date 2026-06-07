package com.innercosmos.service;

import com.innercosmos.entity.AuroraSelfModel;
import com.innercosmos.entity.AuroraSelfStatement;
import com.innercosmos.entity.AuroraSelfReflection;
import java.util.List;

public interface AuroraSelfContinuityService {
    // Layer 1 — record public self statement
    void recordStatement(Long userId, Long sessionId, Long messageId,
                         String statement, String trigger);

    // Layer 2 — log a self reflection event
    void logReflection(Long userId, String trigger, String depth, String summary,
                      Long relatedStatementId, List<String> evidenceRefs);

    // Layer 3 — promote to candidate
    void promoteToCandidate(Long userId, String dimension, String proposedBelief,
                            Double confidence, List<String> evidenceRefs);

    // Layer 4 — commit to long-term model (requires validation)
    void commitToModel(Long userId, Long candidateId,
                       boolean userConfirmed, List<String> extraEvidence);

    // Read — active self model for prompt injection
    List<AuroraSelfModel> getActiveModel(Long userId);

    // Read — recent public statements (for user visibility)
    List<AuroraSelfStatement> getRecentStatements(Long userId, int limit);

    // Read — recent reflections (for user visibility)
    List<AuroraSelfReflection> getRecentReflections(Long userId, int limit);

    // Read — candidate reflections (for audit UI)
    List<AuroraSelfReflection> getCandidates(Long userId);

    // Read — get continuity anchors for prompt
    String getContinuityAnchors(Long userId);

    // User-triggered deep self reflection (LLM-driven)
    String generateUserTriggeredReflection(Long userId, String question);

    // Relationship milestone trigger
    void onRelationshipMilestone(Long userId, String milestoneType);

    // Right to repair — record a repair action
    void recordRepair(Long userId, String ruptureType, String repairAction);

    // Check if a belief is allowed (hard boundary check)
    boolean isAllowedBelief(String belief);
}
