package com.innercosmos.service.impl;

import com.innercosmos.ai.agent.CapsuleAgent;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.PersonaChatMessage;
import com.innercosmos.entity.PersonaChatSession;
import com.innercosmos.mapper.CapsuleBoundaryMapper;
import com.innercosmos.mapper.CapsuleUsageQuotaMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.PersonaChatMessageMapper;
import com.innercosmos.mapper.PersonaChatSessionMapper;
import com.innercosmos.mapper.AuthorizedMemoryRefMapper;
import com.innercosmos.service.CapsuleGenomeService;
import com.innercosmos.service.SafetyService;
import com.innercosmos.service.DataUseGrantService;
import com.innercosmos.vo.SafetyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Gemini audit 2.4 (CONFIRMED/P0), block/revoke recheck: the AI provider RPC now runs with no
 * Spring transaction open (see PersonaChatServiceImplTransactionBoundaryIntegrationTest for that
 * half of the fix). Because the call can take real wall-clock time, the visitor's own block()
 * call or the owner's capsule withdrawal can land in the gap between reserving the turn
 * (prepareTurn) and publishing the reply (finalizeAiTurn). These tests pin that finalizeAiTurn
 * re-checks the authoritative state and refuses to deliver a reply generated against
 * authorization that may no longer hold -- instead compensating the reservation exactly like an
 * AI-unavailable turn.
 */
@ExtendWith(MockitoExtension.class)
class PersonaChatServiceImplBlockRevokeRecheckTest {

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
    @Mock private com.innercosmos.mapper.ReportRecordMapper reportRecordMapper;
    @Mock private com.innercosmos.mapper.BlockRelationMapper blockRelationMapper;
    @Mock private PlatformTransactionManager transactionManager;

    private PersonaChatServiceImpl service;

    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 500L;
    private static final Long CAPSULE_ID = 600L;

    @BeforeEach
    void setUp() {
        service = new PersonaChatServiceImpl(
                sessionMapper, messageMapper, capsuleMapper,
                capsuleAgent, safetyService, structuredAiService,
                boundaryMapper, quotaMapper, jdbcTemplate, authorizedMemoryRefMapper,
                genomeService, runtimeContextComposer, dataUseGrantService,
                reportRecordMapper, blockRelationMapper, transactionManager);
        lenient().when(dataUseGrantService.authorizationsValid(any(), anySet())).thenReturn(true);
        lenient().when(runtimeContextComposer.compose(any(), anyString())).thenReturn(java.util.Map.of(
                "selectedEvidenceSummary", "", "selectedContext", java.util.Map.of(),
                "contextBuildManifest", java.util.Map.of(), "unsupported", true,
                "fallbackPolicy", "ACKNOWLEDGE_UNKNOWN"));
        lenient().when(jdbcTemplate.update(anyString(), any(Long.class))).thenReturn(1);
        lenient().when(jdbcTemplate.update(contains("UPDATE tb_capsule_usage_quota SET turn_count = turn_count + 1"),
                any(Object.class), any(Object.class), any(Object.class), any(Object.class))).thenReturn(1);
        lenient().when(boundaryMapper.selectOne(any())).thenReturn(null);
        lenient().when(messageMapper.insert(any(PersonaChatMessage.class))).thenAnswer(inv -> {
            PersonaChatMessage m = inv.getArgument(0);
            if ("VISITOR".equals(m.senderType)) {
                m.id = 8888L;
            }
            return 1;
        });
    }

    private EchoCapsule activeCapsule() {
        EchoCapsule c = new EchoCapsule();
        c.id = CAPSULE_ID;
        c.capsuleType = "USER_CAPSULE";
        c.isPublic = true;
        c.visibilityStatus = "PUBLIC";
        c.pseudonym = "echo";
        c.intro = "intro";
        c.conversationLimitPerDay = 30;
        return c;
    }

    private PersonaChatSession activeSession() {
        PersonaChatSession s = new PersonaChatSession();
        s.id = SESSION_ID;
        s.visitorUserId = USER_ID;
        s.capsuleId = CAPSULE_ID;
        s.status = "ACTIVE";
        s.turnCount = 0;
        s.dailyLimit = 30;
        return s;
    }

    private SafetyResult safePassed() {
        SafetyResult r = new SafetyResult();
        r.blockModelCall = false;
        r.riskLevel = "LOW";
        return r;
    }

    private StructuredAiResults.PersonaResult goodReply() {
        StructuredAiResults.PersonaResult r = new StructuredAiResults.PersonaResult();
        r.reply = "真实的回声回应，绝不应该被送达";
        r.boundaryNotice = "";
        r.letterSuggested = false;
        return r;
    }

    @Test
    @DisplayName("Visitor blocks the capsule while the provider call is in flight: the reply is never delivered, and the reservation is given back")
    void sessionBlockedDuringProviderCall_compensatesAndDoesNotPublish() {
        PersonaChatSession blockedByTheTimeWeFinalize = activeSession();
        blockedByTheTimeWeFinalize.status = "BLOCKED";
        // First selectById (prepareTurn) sees ACTIVE; second (finalizeAiTurn, after the provider
        // call) sees BLOCKED -- simulating the visitor's own block() call landing mid-flight.
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(activeSession(), blockedByTheTimeWeFinalize);
        when(safetyService.check(any(), any(), any())).thenReturn(safePassed());
        when(capsuleMapper.selectById(CAPSULE_ID)).thenReturn(activeCapsule());
        when(structuredAiService.call(any(), any(), any(), any(), any(), any())).thenReturn(goodReply());

        PersonaChatMessage result = service.reply(USER_ID, SESSION_ID, "hello");

        assertFalse(result.textContent.contains("绝不应该被送达"),
                "a reply generated before the block landed must never reach the (now former) visitor");
        // Both reservations are given back -- symmetric with the AI-unavailable path.
        verify(jdbcTemplate).update(contains("tb_capsule_usage_quota SET turn_count = turn_count - 1"),
                any(Object.class), any(Object.class), any(Object.class));
        verify(jdbcTemplate).update(
                eq("UPDATE tb_persona_chat_session SET turn_count = turn_count - 1 WHERE id = ? AND turn_count > 0"),
                eq(SESSION_ID));
        // The visitor message inserted to feed the (now-discarded) AI call is deleted, same as
        // the AI-unavailable cleanup.
        verify(messageMapper).deleteById(8888L);
        // Energy must not bump for a reply that was never actually delivered.
        verify(capsuleMapper, never()).updateById(any(EchoCapsule.class));
        // The BLOCKED status must stick -- finalizeAiTurn's recheck-failed branch must never
        // touch session.status at all (contrast with the normal path, which always writes it).
        verify(sessionMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("Owner withdraws/archives the capsule while the provider call is in flight: the reply is never delivered, and the reservation is given back")
    void capsuleWithdrawnDuringProviderCall_compensatesAndDoesNotPublish() {
        EchoCapsule withdrawnByTheTimeWeFinalize = activeCapsule();
        withdrawnByTheTimeWeFinalize.isPublic = false;
        withdrawnByTheTimeWeFinalize.visibilityStatus = "ARCHIVED";
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(activeSession());
        when(safetyService.check(any(), any(), any())).thenReturn(safePassed());
        // First selectById (prepareTurn, via requireRunnableCapsule) sees a runnable PUBLIC
        // capsule; second (finalizeAiTurn) sees it withdrawn -- the owner revoked it mid-flight.
        when(capsuleMapper.selectById(CAPSULE_ID)).thenReturn(activeCapsule(), withdrawnByTheTimeWeFinalize);
        when(structuredAiService.call(any(), any(), any(), any(), any(), any())).thenReturn(goodReply());

        PersonaChatMessage result = service.reply(USER_ID, SESSION_ID, "hello");

        assertFalse(result.textContent.contains("绝不应该被送达"),
                "a reply generated before the withdrawal landed must never be delivered");
        verify(jdbcTemplate).update(contains("tb_capsule_usage_quota SET turn_count = turn_count - 1"),
                any(Object.class), any(Object.class), any(Object.class));
        verify(messageMapper).deleteById(8888L);
        verify(capsuleMapper, never()).updateById(any(EchoCapsule.class));
    }

    @Test
    @DisplayName("Nothing changed during the provider call: the recheck passes and the reply is delivered normally")
    void nothingChanged_recheckPassesAndReplyIsDelivered() {
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(activeSession());
        when(safetyService.check(any(), any(), any())).thenReturn(safePassed());
        when(capsuleMapper.selectById(CAPSULE_ID)).thenReturn(activeCapsule());
        when(structuredAiService.call(any(), any(), any(), any(), any(), any())).thenReturn(goodReply());

        PersonaChatMessage result = service.reply(USER_ID, SESSION_ID, "hello");

        assertTrue(result.textContent.contains("绝不应该被送达"), "an unchanged turn must deliver the real reply");
        verify(capsuleMapper).updateById(any(EchoCapsule.class));
        verify(messageMapper, never()).deleteById(any(Long.class));
    }
}
