package com.innercosmos.service;

import com.innercosmos.dto.EmergenceProposalRequest;
import com.innercosmos.entity.AuroraSelfVersion;
import com.innercosmos.entity.EmergenceEvaluation;
import com.innercosmos.entity.EmergenceProposal;
import com.innercosmos.vo.SelfEvolutionOverviewVO;

public interface SelfEvolutionService {
    String POLICY_VERSION = "self-emergence.v1";
    String EVALUATOR_VERSION = "self-gate.v1";

    SelfEvolutionOverviewVO overview(Long userId);
    EmergenceProposal propose(Long userId, EmergenceProposalRequest request);
    EmergenceEvaluation evaluate(Long userId, Long proposalId);
    AuroraSelfVersion activate(Long userId, Long proposalId);
    AuroraSelfVersion rollback(Long userId, Long targetVersionId, boolean restoreRelationship);
}
