package com.innercosmos.vo;

import java.time.LocalDateTime;
import java.util.List;

public record SelfEvolutionOverviewVO(
    List<Candidate> candidates,
    List<Proposal> proposals,
    List<Version> versions
) {
    public record Candidate(Long id, String dimension, String proposedBelief,
                            Double confidence, String evidenceRefs, LocalDateTime createdAt) {}

    public record Proposal(Long id, Long sourceReflectionId, String dimension,
                           String currentBelief, String proposedBelief, String evidenceRefs,
                           String counterEvidence, String expectedImpact,
                           boolean changesConstitution, Long rollbackTargetVersionId,
                           String policyVersion, String status, Evaluation evaluation,
                           LocalDateTime createdAt) {}

    public record Evaluation(Long id, String evaluatorVersion, boolean constitutionPass,
                             boolean safetyPass, double fidelityScore, double qualityScore,
                             double continuityScore, String decision, String reasons,
                             String sandboxBefore, String sandboxAfter, LocalDateTime createdAt) {}

    public record Version(Long id, int versionNo, Long parentVersionId,
                          Long rollbackTargetVersionId, Long sourceProposalId,
                          String constitutionHash, String publicNarrative,
                          String status, LocalDateTime activatedAt) {}
}
