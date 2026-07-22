package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.ai.agent.CapsuleAgent;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.entity.CapsuleBoundary;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Gemini audit 1.4 (CONFIRMED/P0): CapsuleBoundary.maxConversationTurns -- the owner-configured
 * PER-SESSION turn cap -- was written at capsule-creation time but never read anywhere at
 * runtime; only EchoCapsule.conversationLimitPerDay (a DAILY, cross-session cap) was enforced.
 * These tests pin the fix: an independent, atomic per-session cap, checked and reserved BEFORE
 * the daily quota, with symmetric compensation when a later step (daily quota, AI availability)
 * fails.
 */
@ExtendWith(MockitoExtension.class)
class PersonaChatServiceImplSessionCapTest {

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

    private PersonaChatServiceImpl service;

    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 900L;
    private static final Long CAPSULE_ID = 800L;
    private static final String SESSION_RESERVE_SQL =
            "UPDATE tb_persona_chat_session SET turn_count = turn_count + 1 WHERE id = ? AND turn_count < ?";
    private static final String SESSION_COMPENSATE_SQL =
            "UPDATE tb_persona_chat_session SET turn_count = turn_count - 1 WHERE id = ? AND turn_count > 0";

    @BeforeEach
    void setUp() {
        service = new PersonaChatServiceImpl(
                sessionMapper, messageMapper, capsuleMapper,
                capsuleAgent, safetyService, structuredAiService,
                boundaryMapper, quotaMapper, jdbcTemplate, authorizedMemoryRefMapper,
                genomeService, runtimeContextComposer, dataUseGrantService,
                reportRecordMapper, blockRelationMapper);
        lenient().when(dataUseGrantService.authorizationsValid(any(), anySet())).thenReturn(true);
        lenient().when(runtimeContextComposer.compose(any(), anyString())).thenReturn(java.util.Map.of(
                "selectedEvidenceSummary", "", "selectedContext", java.util.Map.of(),
                "contextBuildManifest", java.util.Map.of(), "unsupported", true,
                "fallbackPolicy", "ACKNOWLEDGE_UNKNOWN"));
    }

    private EchoCapsule capsule() {
        EchoCapsule c = new EchoCapsule();
        c.id = CAPSULE_ID;
        c.capsuleType = "USER_CAPSULE";
        c.isPublic = true;
        c.visibilityStatus = "PUBLIC";
        c.pseudonym = "echo";
        c.intro = "intro";
        c.conversationLimitPerDay = 30; // daily cap: deliberately generous, not the thing under test
        return c;
    }

    private PersonaChatSession session() {
        PersonaChatSession s = new PersonaChatSession();
        s.id = SESSION_ID;
        s.visitorUserId = USER_ID;
        s.capsuleId = CAPSULE_ID;
        s.status = "ACTIVE";
        s.turnCount = 0;
        s.dailyLimit = 30;
        return s;
    }

    private CapsuleBoundary boundaryWithCap(int maxTurns) {
        CapsuleBoundary b = new CapsuleBoundary();
        b.capsuleId = CAPSULE_ID;
        b.maxConversationTurns = maxTurns;
        return b;
    }

    private SafetyResult safePassed() {
        SafetyResult r = new SafetyResult();
        r.blockModelCall = false;
        r.riskLevel = "LOW";
        return r;
    }

    private StructuredAiResults.PersonaResult goodReply() {
        StructuredAiResults.PersonaResult r = new StructuredAiResults.PersonaResult();
        r.reply = "真实的回声回应";
        r.boundaryNotice = "";
        r.letterSuggested = false;
        return r;
    }

    private void dailyQuotaReserved() {
        lenient().when(jdbcTemplate.update(contains("UPDATE tb_capsule_usage_quota SET turn_count = turn_count + 1"),
                any(Object.class), any(Object.class), any(Object.class), any(Object.class))).thenReturn(1);
    }

    @Test
    @DisplayName("Session cap exhausted: reply is blocked WITHOUT ever touching the daily quota table")
    void sessionCapExhausted_blocksBeforeTouchingDailyQuota() {
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session());
        when(safetyService.check(any(), any(), any())).thenReturn(safePassed());
        when(capsuleMapper.selectById(CAPSULE_ID)).thenReturn(capsule());
        when(boundaryMapper.selectOne(any())).thenReturn(boundaryWithCap(3));
        // The session's own cap is already exhausted: conditional UPDATE ... WHERE turn_count < 3 matches 0 rows.
        when(jdbcTemplate.update(eq(SESSION_RESERVE_SQL), eq(SESSION_ID), eq(3))).thenReturn(0);

        PersonaChatMessage result = service.reply(USER_ID, SESSION_ID, "one more thing");

        assertTrue(result.textContent.contains("慢信"), "must guide to a slow letter when the session cap is hit");
        assertTrue(result.textContent.contains("轮次上限") || result.textContent.contains("主人设置"),
                "message should explain this is the owner's session-turn cap, distinct from the daily cap");
        verify(structuredAiService, never()).call(any(), any(), any(), any(), any(), any());
        // The daily-quota table must never even be queried/written when the session cap already blocks the turn.
        verify(jdbcTemplate, never()).update(contains("tb_capsule_usage_quota"),
                any(Object.class), any(Object.class), any(Object.class), any(Object.class));
        verify(messageMapper, never()).insert(argThat((PersonaChatMessage m) -> "VISITOR".equals(m.senderType)));
    }

    @Test
    @DisplayName("Under the session cap: turn is reserved atomically and AI is called")
    void underSessionCap_reservesAndCallsAI() {
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session());
        when(safetyService.check(any(), any(), any())).thenReturn(safePassed());
        when(capsuleMapper.selectById(CAPSULE_ID)).thenReturn(capsule());
        when(boundaryMapper.selectOne(any())).thenReturn(boundaryWithCap(5));
        when(jdbcTemplate.update(eq(SESSION_RESERVE_SQL), eq(SESSION_ID), eq(5))).thenReturn(1);
        dailyQuotaReserved();
        when(structuredAiService.call(any(), any(), any(), any(), any(), any())).thenReturn(goodReply());

        PersonaChatMessage result = service.reply(USER_ID, SESSION_ID, "hello");

        assertFalse(result.textContent.contains("慢信"));
        verify(jdbcTemplate).update(eq(SESSION_RESERVE_SQL), eq(SESSION_ID), eq(5));
        verify(structuredAiService).call(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Session cap reserved but daily quota then rejects: the session-turn reservation is compensated (given back)")
    void sessionReservedButDailyQuotaRejects_compensatesSessionTurn() {
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session());
        when(safetyService.check(any(), any(), any())).thenReturn(safePassed());
        when(capsuleMapper.selectById(CAPSULE_ID)).thenReturn(capsule());
        when(boundaryMapper.selectOne(any())).thenReturn(boundaryWithCap(5));
        when(jdbcTemplate.update(eq(SESSION_RESERVE_SQL), eq(SESSION_ID), eq(5))).thenReturn(1);
        // Daily quota is exhausted (a completely independent limit from the session cap).
        when(jdbcTemplate.update(contains("UPDATE tb_capsule_usage_quota SET turn_count = turn_count + 1"),
                any(Object.class), any(Object.class), any(Object.class), any(Object.class))).thenReturn(0);
        when(jdbcTemplate.update(contains("INSERT INTO tb_capsule_usage_quota"),
                any(Object.class), any(Object.class), any(Object.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("dup"));

        PersonaChatMessage result = service.reply(USER_ID, SESSION_ID, "hello");

        assertTrue(result.textContent.contains("慢信"));
        verify(structuredAiService, never()).call(any(), any(), any(), any(), any(), any());
        // The session turn reserved above must be given back since it was never actually used.
        verify(jdbcTemplate).update(eq(SESSION_COMPENSATE_SQL), eq(SESSION_ID));
    }

    @Test
    @DisplayName("AI unavailable: both the daily quota AND the session-turn reservation are compensated")
    void aiUnavailable_compensatesBothQuotaAndSessionTurn() {
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session());
        when(safetyService.check(any(), any(), any())).thenReturn(safePassed());
        when(capsuleMapper.selectById(CAPSULE_ID)).thenReturn(capsule());
        when(boundaryMapper.selectOne(any())).thenReturn(boundaryWithCap(5));
        when(jdbcTemplate.update(eq(SESSION_RESERVE_SQL), eq(SESSION_ID), eq(5))).thenReturn(1);
        dailyQuotaReserved();
        StructuredAiResults.PersonaResult unavailable = new StructuredAiResults.PersonaResult();
        unavailable.riskFlags = java.util.List.of("REMOTE_UNAVAILABLE");
        when(structuredAiService.call(any(), any(), any(), any(), any(), any())).thenReturn(unavailable);

        service.reply(USER_ID, SESSION_ID, "hello");

        verify(jdbcTemplate).update(contains("tb_capsule_usage_quota SET turn_count = turn_count - 1"),
                any(Object.class), any(Object.class), any(Object.class));
        verify(jdbcTemplate).update(eq(SESSION_COMPENSATE_SQL), eq(SESSION_ID));
    }

    @Test
    @DisplayName("Concurrent-turn semantics: the atomic conditional UPDATE means only one of two racing turns can win the last slot")
    void concurrentTurns_onlyOneWinsTheLastSessionSlot() {
        // Simulates two overlapping reply() calls racing for the session's last remaining turn
        // slot: the real atomicity guarantee is the single conditional UPDATE statement itself
        // (WHERE turn_count < cap), not application-level locking. The first caller's UPDATE
        // matches 1 row and wins; a second caller reading the same stale turn_count would still
        // correctly lose, because by the time ITS UPDATE runs, turn_count has already moved.
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session());
        when(safetyService.check(any(), any(), any())).thenReturn(safePassed());
        when(capsuleMapper.selectById(CAPSULE_ID)).thenReturn(capsule());
        when(boundaryMapper.selectOne(any())).thenReturn(boundaryWithCap(1));
        when(jdbcTemplate.update(eq(SESSION_RESERVE_SQL), eq(SESSION_ID), eq(1)))
                .thenReturn(1)  // first caller: turn_count 0 -> 1, wins
                .thenReturn(0); // second (racing) caller: turn_count already 1, no longer < 1, loses
        dailyQuotaReserved();
        when(structuredAiService.call(any(), any(), any(), any(), any(), any())).thenReturn(goodReply());

        PersonaChatMessage first = service.reply(USER_ID, SESSION_ID, "first");
        PersonaChatMessage second = service.reply(USER_ID, SESSION_ID, "second, racing for the same last slot");

        assertFalse(first.textContent.contains("慢信"), "the winner of the race gets a real reply");
        assertTrue(second.textContent.contains("慢信"), "the loser of the race is guided to a slow letter, not double-served");
    }

    @Test
    @DisplayName("Null/zero maxConversationTurns means unlimited: the session cap never blocks")
    void nullOrZeroCap_meansUnlimited() {
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session());
        when(safetyService.check(any(), any(), any())).thenReturn(safePassed());
        when(capsuleMapper.selectById(CAPSULE_ID)).thenReturn(capsule());
        when(boundaryMapper.selectOne(any())).thenReturn(boundaryWithCap(0)); // 0 == not meaningfully configured
        dailyQuotaReserved();
        when(structuredAiService.call(any(), any(), any(), any(), any(), any())).thenReturn(goodReply());

        PersonaChatMessage result = service.reply(USER_ID, SESSION_ID, "hello");

        assertFalse(result.textContent.contains("慢信"));
        // Uncapped path: unconditional increment, never the capped 2-param conditional form.
        verify(jdbcTemplate).update(
                eq("UPDATE tb_persona_chat_session SET turn_count = turn_count + 1 WHERE id = ?"), eq(SESSION_ID));
        verify(jdbcTemplate, never()).update(eq(SESSION_RESERVE_SQL), any(), any());
    }

    @Test
    @DisplayName("The final session write only ever scopes to `status`, never overwriting the atomically-managed turn_count")
    void finalWrite_isScopedToStatusOnly() {
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(session());
        when(safetyService.check(any(), any(), any())).thenReturn(safePassed());
        when(capsuleMapper.selectById(CAPSULE_ID)).thenReturn(capsule());
        when(boundaryMapper.selectOne(any())).thenReturn(boundaryWithCap(5));
        when(jdbcTemplate.update(eq(SESSION_RESERVE_SQL), eq(SESSION_ID), eq(5))).thenReturn(1);
        dailyQuotaReserved();
        when(structuredAiService.call(any(), any(), any(), any(), any(), any())).thenReturn(goodReply());

        service.reply(USER_ID, SESSION_ID, "hello");

        verify(sessionMapper, never()).updateById(any(PersonaChatSession.class));
        verify(sessionMapper).update(eq(null), any(UpdateWrapper.class));
    }
}
