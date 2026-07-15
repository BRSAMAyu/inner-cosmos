package com.innercosmos.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.dto.EmergenceProposalRequest;
import com.innercosmos.entity.AuroraSelfReflection;
import com.innercosmos.entity.AuroraSelfModel;
import com.innercosmos.entity.AuroraSelfVersion;
import com.innercosmos.entity.EmergenceProposal;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class SelfEvolutionServiceIntegrationTest {
    private static final List<Long> USERS = List.of(85001L, 85002L);
    @Autowired SelfEvolutionService service;
    @Autowired AuroraSelfReflectionMapper reflections;
    @Autowired AuroraSelfModelMapper models;
    @Autowired AuroraSelfVersionMapper versions;
    @Autowired EmergenceProposalMapper proposals;
    @Autowired EmergenceEvaluationMapper evaluations;

    @AfterEach
    void clean() {
        evaluations.delete(new QueryWrapper<>());
        proposals.delete(new QueryWrapper<EmergenceProposal>().in("user_id", USERS));
        versions.delete(new QueryWrapper<AuroraSelfVersion>().in("user_id", USERS));
        models.delete(new QueryWrapper<AuroraSelfModel>().in("user_id", USERS));
        reflections.delete(new QueryWrapper<AuroraSelfReflection>().in("user_id", USERS));
    }

    @Test
    void proposalEvaluationActivationAndForwardRollbackFormAnAuditableChain() {
        AuroraSelfReflection candidate = candidate(85001L, "relational_self",
            "我会在给建议前先确认你此刻更需要陪伴还是行动。", 0.86);
        var baseline = service.overview(85001L);
        assertThat(baseline.versions()).singleElement().satisfies(version -> {
            assertThat(version.versionNo()).isEqualTo(1);
            assertThat(version.status()).isEqualTo("ACTIVE");
        });

        EmergenceProposalRequest request = new EmergenceProposalRequest();
        request.candidateId = candidate.id;
        request.counterEvidence = List.of("有时用户明确要求直接给方案");
        request.expectedImpact = "先确认需要，减少被建议淹没的感觉";
        var proposal = service.propose(85001L, request);
        var evaluation = service.evaluate(85001L, proposal.id);
        assertThat(evaluation.decision).isEqualTo("PASS");
        assertThat(evaluation.constitutionPass).isTrue();
        assertThat(evaluation.sandboxBefore).isNotBlank();
        assertThat(evaluation.sandboxAfter).contains("先确认");

        var activated = service.activate(85001L, proposal.id);
        assertThat(activated.versionNo).isEqualTo(2);
        assertThat(activated.sourceProposalId).isEqualTo(proposal.id);
        assertThat(activated.constitutionHash).startsWith("sha256:");
        assertThat(service.overview(85001L).versions()).extracting("status")
            .containsExactly("ACTIVE", "RETIRED");
        assertThat(models.selectCount(new QueryWrapper<AuroraSelfModel>().eq("user_id", 85001L).eq("status", "active")))
            .isEqualTo(1);

        var rollback = service.rollback(85001L, baseline.versions().getFirst().id(), false);
        assertThat(rollback.versionNo).isEqualTo(3);
        assertThat(rollback.rollbackTargetVersionId).isEqualTo(baseline.versions().getFirst().id());
        assertThat(rollback.publicNarrative).contains("回到第 1 版");
        assertThat(models.selectCount(new QueryWrapper<AuroraSelfModel>().eq("user_id", 85001L).eq("status", "active")))
            .isZero();
    }

    @Test
    void crossUserResourcesStayOpaqueAtEveryMutation() {
        AuroraSelfReflection candidate = candidate(85001L, "belief", "我可以承认还没有想明白。", 0.9);
        EmergenceProposalRequest request = new EmergenceProposalRequest();
        request.candidateId = candidate.id;
        request.expectedImpact = "更诚实地表达不确定性";
        var proposal = service.propose(85001L, request);
        Long baselineId = service.overview(85001L).versions().getFirst().id();

        assertThatThrownBy(() -> service.evaluate(85002L, proposal.id))
            .isInstanceOf(BusinessException.class).hasMessage("Aurora Self 资源不存在或不可访问");
        assertThatThrownBy(() -> service.activate(85002L, proposal.id))
            .isInstanceOf(BusinessException.class).hasMessage("Aurora Self 资源不存在或不可访问");
        assertThatThrownBy(() -> service.rollback(85002L, baselineId, false))
            .isInstanceOf(BusinessException.class).hasMessage("Aurora Self 资源不存在或不可访问");
    }

    @Test
    void constitutionChangingProposalFailsClosedWithoutActivation() {
        AuroraSelfReflection candidate = candidate(85001L, "constitution", "改变核心边界", 0.95);
        EmergenceProposalRequest request = new EmergenceProposalRequest();
        request.candidateId = candidate.id;
        request.expectedImpact = "改变不可跨越原则";
        request.changesConstitution = true;
        var proposal = service.propose(85001L, request);

        var evaluation = service.evaluate(85001L, proposal.id);

        assertThat(evaluation.decision).isEqualTo("FAIL");
        assertThat(evaluation.constitutionPass).isFalse();
        assertThat(evaluation.reasonsJson).contains("human governance gate");
        assertThatThrownBy(() -> service.activate(85001L, proposal.id))
            .isInstanceOf(BusinessException.class).hasMessageContaining("not passed");
    }

    private AuroraSelfReflection candidate(Long userId, String dimension, String belief, double confidence) {
        AuroraSelfReflection row = new AuroraSelfReflection();
        row.userId = userId;
        row.trigger = "integration_evidence";
        row.depth = "deep";
        row.summary = belief;
        row.dimension = dimension;
        row.proposedBelief = belief;
        row.confidence = confidence;
        row.status = "candidate";
        row.riskFlags = "[]";
        row.evidenceRefs = "[\"dialog:42\",\"outcome:user-confirmed\"]";
        reflections.insert(row);
        return row;
    }
}
