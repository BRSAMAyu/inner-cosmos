package com.innercosmos.service.impl;

import com.innercosmos.ai.agent.CapsuleAgent;
import com.innercosmos.ai.context.AgentContext;
import com.innercosmos.ai.context.AgentContextAssembler;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.entity.CapsuleUsageQuota;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.PersonaChatMessage;
import com.innercosmos.entity.PersonaChatSession;
import com.innercosmos.mapper.CapsuleBoundaryMapper;
import com.innercosmos.mapper.CapsuleUsageQuotaMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.PersonaChatMessageMapper;
import com.innercosmos.mapper.PersonaChatSessionMapper;
import com.innercosmos.mapper.AuthorizedMemoryRefMapper;
import com.innercosmos.service.SafetyService;
import com.innercosmos.vo.SafetyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * IC-CAP-001: Unit tests for PersonaChatServiceImpl quota enforcement.
 * Tests SEED capsule daily limit fix and cross-session per-day quota tracking.
 *
 * After Fix 1 (atomic quota), tryReserveQuota() uses only JdbcTemplate:
 *   1. jdbcTemplate.update(conditional UPDATE ...) — returns 1 if slot reserved, 0 if row missing or at limit
 *   2. jdbcTemplate.update(INSERT ...)             — for first turn of the day
 * The old quotaMapper.selectOne() is no longer called.
 * quotaMapper.insert() is no longer called (replaced by jdbcTemplate INSERT).
 */
@ExtendWith(MockitoExtension.class)
class PersonaChatServiceImplQuotaTest {

    @Mock private PersonaChatSessionMapper sessionMapper;
    @Mock private PersonaChatMessageMapper messageMapper;
    @Mock private EchoCapsuleMapper capsuleMapper;
    @Mock private CapsuleAgent capsuleAgent;
    @Mock private SafetyService safetyService;
    @Mock private StructuredAiService structuredAiService;
    @Mock private CapsuleBoundaryMapper boundaryMapper;
    @Mock private MemoryCardMapper memoryCardMapper;
    @Mock private AgentContextAssembler agentContextAssembler;
    @Mock private CapsuleUsageQuotaMapper quotaMapper;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private AuthorizedMemoryRefMapper authorizedMemoryRefMapper;

    private PersonaChatServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PersonaChatServiceImpl(
                sessionMapper, messageMapper, capsuleMapper,
                capsuleAgent, safetyService, structuredAiService,
                boundaryMapper, memoryCardMapper, agentContextAssembler,
                quotaMapper, jdbcTemplate, authorizedMemoryRefMapper);
    }

    // ──────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────

    private EchoCapsule publicCapsule(Long id, String capsuleType) {
        EchoCapsule c = new EchoCapsule();
        c.id = id;
        c.capsuleType = capsuleType;
        c.isPublic = true;
        c.visibilityStatus = "PUBLIC";
        c.pseudonym = "echo";
        c.intro = "test capsule";
        return c;
    }

    private EchoCapsule publicCapsuleWithLimit(Long id, String capsuleType, int limit) {
        EchoCapsule c = publicCapsule(id, capsuleType);
        c.conversationLimitPerDay = limit;
        return c;
    }

    private PersonaChatSession activeSession(Long sessionId, Long userId, Long capsuleId, int turnCount, int dailyLimit) {
        PersonaChatSession s = new PersonaChatSession();
        s.id = sessionId;
        s.visitorUserId = userId;
        s.capsuleId = capsuleId;
        s.status = "ACTIVE";
        s.turnCount = turnCount;
        s.dailyLimit = dailyLimit;
        return s;
    }

    private SafetyResult safePassed() {
        SafetyResult r = new SafetyResult();
        r.blockModelCall = false;
        r.riskLevel = "LOW";
        return r;
    }

    private StructuredAiResults.PersonaResult aiResult(String reply) {
        StructuredAiResults.PersonaResult r = new StructuredAiResults.PersonaResult();
        r.reply = reply;
        r.boundaryNotice = "";
        r.letterSuggested = false;
        return r;
    }

    // ──────────────────────────────────────────────────────
    // Bug 1: SEED capsule daily limit
    // ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Bug 1 fix: SEED_CAPSULE type gets dailyLimit=50, not 0 (unlimited)")
    void create_seedCapsule_gets50DailyLimit() {
        Long userId = 1L;
        Long capsuleId = 100L;
        EchoCapsule capsule = publicCapsule(capsuleId, "SEED_CAPSULE");
        when(capsuleMapper.selectById(capsuleId)).thenReturn(capsule);

        ArgumentCaptor<PersonaChatSession> cap = ArgumentCaptor.forClass(PersonaChatSession.class);
        service.create(userId, capsuleId);
        verify(sessionMapper).insert(cap.capture());

        PersonaChatSession session = cap.getValue();
        assertEquals(50, session.dailyLimit, "SEED_CAPSULE must get dailyLimit=50, not 0");
    }

    @Test
    @DisplayName("Bug 1 fix: SEED type (alias) also gets dailyLimit=50")
    void create_seedTypeAlias_gets50DailyLimit() {
        Long userId = 2L;
        Long capsuleId = 101L;
        EchoCapsule capsule = publicCapsule(capsuleId, "SEED");
        when(capsuleMapper.selectById(capsuleId)).thenReturn(capsule);

        ArgumentCaptor<PersonaChatSession> cap = ArgumentCaptor.forClass(PersonaChatSession.class);
        service.create(userId, capsuleId);
        verify(sessionMapper).insert(cap.capture());

        assertEquals(50, cap.getValue().dailyLimit, "SEED type alias must get dailyLimit=50");
    }

    @Test
    @DisplayName("Non-SEED capsule still uses conversationLimitPerDay or defaults to 30")
    void create_nonSeedCapsule_usesConfiguredLimit() {
        Long userId = 3L;
        Long capsuleId = 102L;
        EchoCapsule capsule = publicCapsuleWithLimit(capsuleId, "USER_CAPSULE", 15);
        when(capsuleMapper.selectById(capsuleId)).thenReturn(capsule);

        ArgumentCaptor<PersonaChatSession> cap = ArgumentCaptor.forClass(PersonaChatSession.class);
        service.create(userId, capsuleId);
        verify(sessionMapper).insert(cap.capture());

        assertEquals(15, cap.getValue().dailyLimit);
    }

    // ──────────────────────────────────────────────────────
    // Fix 2: SEED cap is absolute — never overridden by conversationLimitPerDay
    // ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Fix 2: SEED capsule with conversationLimitPerDay set still gets dailyLimit=50 (effective cap)")
    void create_seedCapsuleWithExplicitLimit_stillGets50() {
        Long userId = 4L;
        Long capsuleId = 103L;
        // Even though conversationLimitPerDay is set to 99, SEED effective cap is 50
        EchoCapsule capsule = publicCapsuleWithLimit(capsuleId, "SEED_CAPSULE", 99);
        when(capsuleMapper.selectById(capsuleId)).thenReturn(capsule);

        ArgumentCaptor<PersonaChatSession> cap = ArgumentCaptor.forClass(PersonaChatSession.class);
        service.create(userId, capsuleId);
        verify(sessionMapper).insert(cap.capture());

        assertEquals(50, cap.getValue().dailyLimit,
                "SEED capsule conversationLimitPerDay must NOT override the effective cap of 50");
    }

    // ──────────────────────────────────────────────────────
    // Bug 2: Cross-session daily quota enforcement
    // tryReserveQuota flow:
    //   - jdbcTemplate.update(UPDATE ...) → 1 means reserved, 0 means no row or at limit
    //   - jdbcTemplate.update(INSERT ...) → succeeds means first turn, DuplicateKeyException means at limit
    // ──────────────────────────────────────────────────────

    /**
     * Helper: mock jdbcTemplate so that the conditional UPDATE returns 0 and
     * the INSERT throws DuplicateKeyException — simulating "quota already at limit".
     */
    private void mockQuotaAtLimit() {
        // First jdbcTemplate.update call = conditional UPDATE (4 params: userId, capsuleId, today, dailyLimit)
        // Returns 0 = row exists but at limit (turn_count not < dailyLimit)
        // Second jdbcTemplate.update call = INSERT (3 params: userId, capsuleId, today)
        // Throws DuplicateKeyException = row already exists (limit hit, not first turn)
        when(jdbcTemplate.update(
                contains("UPDATE tb_capsule_usage_quota SET turn_count = turn_count + 1"),
                any(Object.class), any(Object.class), any(Object.class), any(Object.class)))
                .thenReturn(0);
        when(jdbcTemplate.update(
                contains("INSERT INTO tb_capsule_usage_quota"),
                any(Object.class), any(Object.class), any(Object.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
    }

    /**
     * Helper: mock jdbcTemplate so that the conditional UPDATE returns 1 — simulating "slot reserved".
     */
    private void mockQuotaReservedViaUpdate() {
        when(jdbcTemplate.update(
                contains("UPDATE tb_capsule_usage_quota SET turn_count = turn_count + 1"),
                any(Object.class), any(Object.class), any(Object.class), any(Object.class)))
                .thenReturn(1);
    }

    /**
     * Helper: mock jdbcTemplate so that UPDATE returns 0 (no row yet) and INSERT succeeds.
     * Simulates first turn of the day.
     */
    private void mockQuotaReservedViaInsert() {
        when(jdbcTemplate.update(
                contains("UPDATE tb_capsule_usage_quota SET turn_count = turn_count + 1"),
                any(Object.class), any(Object.class), any(Object.class), any(Object.class)))
                .thenReturn(0);
        when(jdbcTemplate.update(
                contains("INSERT INTO tb_capsule_usage_quota"),
                any(Object.class), any(Object.class), any(Object.class)))
                .thenReturn(1);
    }

    @Test
    @DisplayName("Bug 2 fix: when quota is at daily limit, reply returns LETTER_GUIDED status")
    void reply_quotaAtLimit_returnsLetterGuided() {
        Long userId = 10L;
        Long sessionId = 200L;
        Long capsuleId = 300L;

        EchoCapsule capsule = publicCapsuleWithLimit(capsuleId, "USER_CAPSULE", 5);
        PersonaChatSession session = activeSession(sessionId, userId, capsuleId, 0, 5);

        when(sessionMapper.selectById(sessionId)).thenReturn(session);
        when(safetyService.check(any(), any(), any())).thenReturn(safePassed());
        when(capsuleMapper.selectById(capsuleId)).thenReturn(capsule);
        mockQuotaAtLimit();

        PersonaChatMessage result = service.reply(userId, sessionId, "hello");

        assertTrue(result.textContent.contains("慢信"),
                "Response must contain '慢信' when daily limit is reached");
        assertEquals("LETTER_GUIDED", session.status);

        // AI must NOT have been called
        verify(structuredAiService, never()).call(any(), any(), any(), any(), any(), any());
        // quotaMapper.insert must NOT be called — we only use jdbcTemplate now
        verify(quotaMapper, never()).insert(any(CapsuleUsageQuota.class));
    }

    @Test
    @DisplayName("Bug 2 fix: when quota row exists and below limit, UPDATE succeeds and AI is called")
    void reply_quotaBelowLimit_updatesQuotaAndCallsAI() {
        Long userId = 11L;
        Long sessionId = 201L;
        Long capsuleId = 301L;

        EchoCapsule capsule = publicCapsuleWithLimit(capsuleId, "USER_CAPSULE", 5);
        PersonaChatSession session = activeSession(sessionId, userId, capsuleId, 2, 5);

        when(sessionMapper.selectById(sessionId)).thenReturn(session);
        when(safetyService.check(any(), any(), any())).thenReturn(safePassed());
        when(capsuleMapper.selectById(capsuleId)).thenReturn(capsule);
        mockQuotaReservedViaUpdate();

        when(structuredAiService.call(any(), any(), any(), any(), any(), any()))
                .thenReturn(aiResult("回声回应"));
        when(boundaryMapper.selectOne(any())).thenReturn(null);

        PersonaChatMessage result = service.reply(userId, sessionId, "hello");

        assertNotNull(result.textContent);
        assertFalse(result.textContent.contains("慢信"),
                "Should NOT return letter-guided message when quota is below limit");

        // Quota must be incremented via atomic conditional UPDATE
        verify(jdbcTemplate).update(
                contains("UPDATE tb_capsule_usage_quota SET turn_count = turn_count + 1"),
                any(Object.class), any(Object.class), any(Object.class), any(Object.class));
        // INSERT must NOT be called since UPDATE succeeded
        verify(jdbcTemplate, never()).update(
                contains("INSERT INTO tb_capsule_usage_quota"),
                any(Object.class), any(Object.class), any(Object.class));

        // session status reset to ACTIVE
        assertEquals("ACTIVE", session.status);
    }

    @Test
    @DisplayName("Bug 2 fix: no existing quota record => INSERT new record with turnCount=1 via JdbcTemplate")
    void reply_noExistingQuota_insertsNewRecord() {
        Long userId = 12L;
        Long sessionId = 202L;
        Long capsuleId = 302L;

        EchoCapsule capsule = publicCapsuleWithLimit(capsuleId, "USER_CAPSULE", 5);
        PersonaChatSession session = activeSession(sessionId, userId, capsuleId, 0, 5);

        when(sessionMapper.selectById(sessionId)).thenReturn(session);
        when(safetyService.check(any(), any(), any())).thenReturn(safePassed());
        when(capsuleMapper.selectById(capsuleId)).thenReturn(capsule);
        mockQuotaReservedViaInsert(); // UPDATE returns 0 (no row), INSERT succeeds

        when(structuredAiService.call(any(), any(), any(), any(), any(), any()))
                .thenReturn(aiResult("新的回声"));
        when(boundaryMapper.selectOne(any())).thenReturn(null);

        service.reply(userId, sessionId, "first message");

        // INSERT must have been called via jdbcTemplate
        verify(jdbcTemplate).update(
                contains("INSERT INTO tb_capsule_usage_quota"),
                any(Object.class), any(Object.class), any(Object.class));
        // quotaMapper.insert must NOT be called
        verify(quotaMapper, never()).insert(any(CapsuleUsageQuota.class));
    }

    @Test
    @DisplayName("Cross-session enforcement: second session for same user+capsule+day uses same quota")
    void reply_twoSessions_shareQuota() {
        Long userId = 13L;
        Long capsuleId = 303L;
        Long sessionId2 = 204L;

        EchoCapsule capsule = publicCapsuleWithLimit(capsuleId, "USER_CAPSULE", 3);

        when(sessionMapper.selectById(sessionId2)).thenReturn(
                activeSession(sessionId2, userId, capsuleId, 0, 3)); // fresh session, 0 turns
        when(safetyService.check(any(), any(), any())).thenReturn(safePassed());
        when(capsuleMapper.selectById(capsuleId)).thenReturn(capsule);
        mockQuotaAtLimit(); // quota already at limit cross-session

        PersonaChatMessage result = service.reply(userId, sessionId2, "second session msg");

        // Even though the session's own turnCount is 0, the cross-session daily quota blocks it
        assertTrue(result.textContent.contains("慢信"),
                "Cross-session quota must be enforced: second session can't bypass daily limit");
    }

    @Test
    @DisplayName("SEED capsule: resolveDailyLimit uses 50 when no conversationLimitPerDay set")
    void reply_seedCapsuleNoLimit_uses50AsDefault() {
        Long userId = 14L;
        Long sessionId = 205L;
        Long capsuleId = 304L;

        // SEED capsule with NO explicit conversationLimitPerDay — should default to 50
        EchoCapsule capsule = publicCapsule(capsuleId, "SEED_CAPSULE");
        PersonaChatSession session = activeSession(sessionId, userId, capsuleId, 0, 50);

        when(sessionMapper.selectById(sessionId)).thenReturn(session);
        when(safetyService.check(any(), any(), any())).thenReturn(safePassed());
        when(capsuleMapper.selectById(capsuleId)).thenReturn(capsule);
        mockQuotaAtLimit(); // quota at 50 (SEED effective limit)

        PersonaChatMessage result = service.reply(userId, sessionId, "msg");

        assertTrue(result.textContent.contains("慢信"),
                "SEED capsule must be blocked at 50 (not unlimited) when quota is at 50");
    }

    @Test
    @DisplayName("Fix 3: session status resets to ACTIVE after a successful reply (not stuck in LETTER_GUIDED)")
    void reply_afterLetterGuided_statusResetsToActive() {
        Long userId = 15L;
        Long sessionId = 206L;
        Long capsuleId = 305L;

        EchoCapsule capsule = publicCapsuleWithLimit(capsuleId, "USER_CAPSULE", 5);
        // Session was previously LETTER_GUIDED
        PersonaChatSession session = activeSession(sessionId, userId, capsuleId, 3, 5);
        session.status = "LETTER_GUIDED";

        when(sessionMapper.selectById(sessionId)).thenReturn(session);
        when(safetyService.check(any(), any(), any())).thenReturn(safePassed());
        when(capsuleMapper.selectById(capsuleId)).thenReturn(capsule);
        mockQuotaReservedViaUpdate(); // below limit this time

        when(structuredAiService.call(any(), any(), any(), any(), any(), any()))
                .thenReturn(aiResult("回声回应"));
        when(boundaryMapper.selectOne(any())).thenReturn(null);

        service.reply(userId, sessionId, "hello again");

        assertEquals("ACTIVE", session.status,
                "Fix 3: session.status must be reset to ACTIVE after a successful reply");
    }
}
