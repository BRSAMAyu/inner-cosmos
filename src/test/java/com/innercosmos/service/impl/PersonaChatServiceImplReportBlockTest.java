package com.innercosmos.service.impl;

import com.innercosmos.ai.agent.CapsuleAgent;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.entity.BlockRelation;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.PersonaChatSession;
import com.innercosmos.entity.ReportRecord;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.AuthorizedMemoryRefMapper;
import com.innercosmos.mapper.BlockRelationMapper;
import com.innercosmos.mapper.CapsuleBoundaryMapper;
import com.innercosmos.mapper.CapsuleUsageQuotaMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.PersonaChatMessageMapper;
import com.innercosmos.mapper.PersonaChatSessionMapper;
import com.innercosmos.mapper.ReportRecordMapper;
import com.innercosmos.service.CapsuleGenomeService;
import com.innercosmos.service.DataUseGrantService;
import com.innercosmos.service.SafetyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * A visitor mid-conversation with a capsule (not just after a letter is delivered) must be
 * able to report a persona session for review, or block the capsule owner so future capsules
 * from that owner stop surfacing as resonance matches (see CapsuleServiceImpl.blockedCounterparties).
 */
@ExtendWith(MockitoExtension.class)
class PersonaChatServiceImplReportBlockTest {

    @Mock private PersonaChatSessionMapper sessionMapper;
    @Mock private PersonaChatMessageMapper messageMapper;
    @Mock private EchoCapsuleMapper capsuleMapper;
    @Mock private CapsuleAgent capsuleAgent;
    @Mock private SafetyService safetyService;
    @Mock private StructuredAiService structuredAiService;
    @Mock private CapsuleBoundaryMapper boundaryMapper;
    @Mock private CapsuleUsageQuotaMapper quotaMapper;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private AuthorizedMemoryRefMapper authorizedMemoryRefMapper;
    @Mock private CapsuleGenomeService genomeService;
    @Mock private CapsuleRuntimeContextComposer runtimeContextComposer;
    @Mock private DataUseGrantService dataUseGrantService;
    @Mock private ReportRecordMapper reportRecordMapper;
    @Mock private BlockRelationMapper blockRelationMapper;
    @Mock private PlatformTransactionManager transactionManager;

    private PersonaChatServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PersonaChatServiceImpl(
                sessionMapper, messageMapper, capsuleMapper,
                capsuleAgent, safetyService, structuredAiService,
                boundaryMapper, quotaMapper, jdbcTemplate, authorizedMemoryRefMapper,
                genomeService, runtimeContextComposer, dataUseGrantService,
                reportRecordMapper, blockRelationMapper, transactionManager);
    }

    private PersonaChatSession session(Long id, Long visitorUserId, Long capsuleId) {
        PersonaChatSession s = new PersonaChatSession();
        s.id = id;
        s.visitorUserId = visitorUserId;
        s.capsuleId = capsuleId;
        s.status = "ACTIVE";
        return s;
    }

    @Test
    @DisplayName("report() records a pending report for the session, scoped to the reporting visitor")
    void report_recordsPendingReport() {
        when(sessionMapper.selectById(30L)).thenReturn(session(30L, 1L, 200L));

        service.report(1L, 30L, "对方的回应让我不舒服");

        ArgumentCaptor<ReportRecord> cap = ArgumentCaptor.forClass(ReportRecord.class);
        verify(reportRecordMapper).insert(cap.capture());
        ReportRecord saved = cap.getValue();
        assertEquals(1L, saved.reporterUserId);
        assertEquals("PERSONA_CHAT_SESSION", saved.targetType);
        assertEquals(30L, saved.targetId);
        assertEquals("对方的回应让我不舒服", saved.reason);
        assertEquals("PENDING", saved.status);
    }

    @Test
    @DisplayName("report() rejects a user who is not the session's own visitor")
    void report_rejectsNonVisitor() {
        when(sessionMapper.selectById(30L)).thenReturn(session(30L, 1L, 200L));

        var error = assertThrows(BusinessException.class, () -> service.report(999L, 30L, "reason"));

        assertEquals("UNAUTHORIZED", error.code);
        verifyNoInteractions(reportRecordMapper);
    }

    @Test
    @DisplayName("block() blocks the capsule's owner so their capsules stop matching this visitor, and marks the session BLOCKED")
    void block_blocksCapsuleOwnerAndMarksSessionBlocked() {
        when(sessionMapper.selectById(31L)).thenReturn(session(31L, 1L, 201L));
        EchoCapsule capsule = new EchoCapsule();
        capsule.id = 201L;
        capsule.ownerUserId = 55L;
        when(capsuleMapper.selectById(201L)).thenReturn(capsule);
        when(blockRelationMapper.selectCount(any())).thenReturn(0L);

        service.block(1L, 31L);

        ArgumentCaptor<BlockRelation> cap = ArgumentCaptor.forClass(BlockRelation.class);
        verify(blockRelationMapper).insert(cap.capture());
        BlockRelation saved = cap.getValue();
        assertEquals(1L, saved.blockerUserId);
        assertEquals(55L, saved.blockedUserId);

        ArgumentCaptor<PersonaChatSession> sessionCap = ArgumentCaptor.forClass(PersonaChatSession.class);
        verify(sessionMapper).updateById(sessionCap.capture());
        assertEquals("BLOCKED", sessionCap.getValue().status);
    }

    @Test
    @DisplayName("block() does not insert a duplicate BlockRelation if one already exists")
    void block_doesNotDuplicateExistingBlock() {
        when(sessionMapper.selectById(32L)).thenReturn(session(32L, 1L, 202L));
        EchoCapsule capsule = new EchoCapsule();
        capsule.id = 202L;
        capsule.ownerUserId = 56L;
        when(capsuleMapper.selectById(202L)).thenReturn(capsule);
        when(blockRelationMapper.selectCount(any())).thenReturn(1L);

        service.block(1L, 32L);

        verify(blockRelationMapper, never()).insert(any(BlockRelation.class));
    }

    @Test
    @DisplayName("block() rejects a user who is not the session's own visitor")
    void block_rejectsNonVisitor() {
        when(sessionMapper.selectById(31L)).thenReturn(session(31L, 1L, 201L));

        var error = assertThrows(BusinessException.class, () -> service.block(999L, 31L));

        assertEquals("UNAUTHORIZED", error.code);
        verifyNoInteractions(blockRelationMapper);
    }
}
