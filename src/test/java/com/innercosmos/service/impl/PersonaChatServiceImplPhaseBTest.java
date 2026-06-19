package com.innercosmos.service.impl;

import com.innercosmos.ai.agent.CapsuleAgent;
import com.innercosmos.ai.context.AgentContext;
import com.innercosmos.ai.context.AgentContextAssembler;
import com.innercosmos.ai.structured.StructuredAiResults;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.PersonaChatMessage;
import com.innercosmos.entity.PersonaChatSession;
import com.innercosmos.mapper.CapsuleBoundaryMapper;
import com.innercosmos.mapper.CapsuleUsageQuotaMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.PersonaChatMessageMapper;
import com.innercosmos.mapper.PersonaChatSessionMapper;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * IC-CAP-002 Phase-B + folded Phase-A MAJOR fixes in PersonaChatServiceImpl:
 *   - B-4   echo energy bump on a genuinely successful turn
 *   - MAJOR-1 AI-unavailable does not consume quota (reserved → compensated)
 *   - MAJOR-2 over-limit does not persist the visitor message
 *   - MAJOR-3 first-day insert collision resolves via a retry UPDATE
 */
@ExtendWith(MockitoExtension.class)
class PersonaChatServiceImplPhaseBTest {

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

    private PersonaChatServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PersonaChatServiceImpl(
                sessionMapper, messageMapper, capsuleMapper,
                capsuleAgent, safetyService, structuredAiService,
                boundaryMapper, memoryCardMapper, agentContextAssembler,
                quotaMapper, jdbcTemplate);
    }

    private EchoCapsule capsule(Long id) {
        EchoCapsule c = new EchoCapsule();
        c.id = id;
        c.capsuleType = "USER_CAPSULE";
        c.isPublic = true;
        c.visibilityStatus = "PUBLIC";
        c.pseudonym = "echo";
        c.intro = "intro";
        c.conversationLimitPerDay = 5;
        c.echoEnergy = 0.50;
        c.freshnessScore = 0.20;
        return c;
    }

    private PersonaChatSession session(Long sessionId, Long userId, Long capsuleId) {
        PersonaChatSession s = new PersonaChatSession();
        s.id = sessionId;
        s.visitorUserId = userId;
        s.capsuleId = capsuleId;
        s.status = "ACTIVE";
        s.turnCount = 0;
        s.dailyLimit = 5;
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
        r.reply = "真实的回声回应";
        r.boundaryNotice = "";
        r.letterSuggested = false;
        return r;
    }

    private StructuredAiResults.PersonaResult unavailable() {
        StructuredAiResults.PersonaResult r = new StructuredAiResults.PersonaResult();
        r.reply = "真实模型暂时不可用，我不想用模板伪装成这个共鸣体。请稍后再试，或者写一封慢信。";
        r.boundaryNotice = "模型状态提示：";
        r.letterSuggested = true;
        r.riskFlags = List.of("REMOTE_UNAVAILABLE");
        return r;
    }

    private void reserveViaUpdate() {
        when(jdbcTemplate.update(contains("UPDATE tb_capsule_usage_quota SET turn_count = turn_count + 1"),
                any(Object.class), any(Object.class), any(Object.class), any(Object.class)))
                .thenReturn(1);
    }

    @Test
    @DisplayName("B-4: a genuinely successful turn bumps echoEnergy (+0.02), freshness>=0.9, lastActivityAt set")
    void energyBumps_onSuccessfulTurn() {
        Long userId = 1L, sessionId = 10L, capsuleId = 100L;
        EchoCapsule c = capsule(capsuleId);
        when(sessionMapper.selectById(sessionId)).thenReturn(session(sessionId, userId, capsuleId));
        when(safetyService.check(any(), any(), any())).thenReturn(safePassed());
        when(capsuleMapper.selectById(capsuleId)).thenReturn(c);
        reserveViaUpdate();
        when(agentContextAssembler.assemble(any(), any(), any(), anyBoolean())).thenReturn(new AgentContext());
        when(structuredAiService.call(any(), any(), any(), any(), any(), any())).thenReturn(goodReply());
        when(boundaryMapper.selectOne(any())).thenReturn(null);

        service.reply(userId, sessionId, "hi");

        ArgumentCaptor<EchoCapsule> cap = ArgumentCaptor.forClass(EchoCapsule.class);
        verify(capsuleMapper).updateById(cap.capture());
        EchoCapsule saved = cap.getValue();
        assertEquals(0.52, saved.echoEnergy, 1e-9, "echoEnergy bumped by 0.02");
        assertTrue(saved.freshnessScore >= 0.9, "freshness pulled up to >= 0.9");
        assertNotNull(saved.lastActivityAt, "lastActivityAt must be set on success");

        // IC-CAP-002 FIX-3: a genuinely answered turn DOES advance session.turnCount
        // (0 → 1), staying in lock-step with the consumed day quota.
        ArgumentCaptor<PersonaChatSession> sCap = ArgumentCaptor.forClass(PersonaChatSession.class);
        verify(sessionMapper).updateById(sCap.capture());
        assertEquals(1, sCap.getValue().turnCount,
                "a successful turn must increment session.turnCount");
    }

    @Test
    @DisplayName("MAJOR-1: AI-unavailable compensates the reserved quota turn and does NOT bump energy")
    void aiUnavailable_doesNotConsumeQuota() {
        Long userId = 2L, sessionId = 11L, capsuleId = 101L;
        EchoCapsule c = capsule(capsuleId);
        PersonaChatSession s = session(sessionId, userId, capsuleId);
        s.turnCount = 3; // pre-existing count; must NOT advance on an unanswered turn
        when(sessionMapper.selectById(sessionId)).thenReturn(s);
        when(safetyService.check(any(), any(), any())).thenReturn(safePassed());
        when(capsuleMapper.selectById(capsuleId)).thenReturn(c);
        reserveViaUpdate();
        when(agentContextAssembler.assemble(any(), any(), any(), anyBoolean())).thenReturn(new AgentContext());
        when(structuredAiService.call(any(), any(), any(), any(), any(), any())).thenReturn(unavailable());
        when(boundaryMapper.selectOne(any())).thenReturn(null);

        service.reply(userId, sessionId, "hi");

        // Quota was reserved (UPDATE +1) then compensated (UPDATE -1, conditional turn_count > 0).
        verify(jdbcTemplate).update(contains("turn_count = turn_count - 1"),
                any(Object.class), any(Object.class), any(Object.class));
        // Energy must NOT be bumped on the unavailable path.
        verify(capsuleMapper, never()).updateById(any(EchoCapsule.class));

        // IC-CAP-002 FIX-3: an unanswered turn un-charges the day quota, so session.turnCount
        // must NOT advance — otherwise the session counter and the day quota diverge.
        ArgumentCaptor<PersonaChatSession> sCap = ArgumentCaptor.forClass(PersonaChatSession.class);
        verify(sessionMapper).updateById(sCap.capture());
        assertEquals(3, sCap.getValue().turnCount,
                "AI-unavailable turn must NOT increment session.turnCount");
    }

    @Test
    @DisplayName("MAJOR-1: over-limit gate still blocks without calling AI (net-zero reserve does not weaken the gate)")
    void overLimit_stillGated() {
        Long userId = 3L, sessionId = 12L, capsuleId = 102L;
        EchoCapsule c = capsule(capsuleId);
        when(sessionMapper.selectById(sessionId)).thenReturn(session(sessionId, userId, capsuleId));
        when(safetyService.check(any(), any(), any())).thenReturn(safePassed());
        when(capsuleMapper.selectById(capsuleId)).thenReturn(c);
        // UPDATE returns 0 (at limit) and INSERT throws Duplicate (row exists at limit) → not reserved.
        when(jdbcTemplate.update(contains("UPDATE tb_capsule_usage_quota SET turn_count = turn_count + 1"),
                any(Object.class), any(Object.class), any(Object.class), any(Object.class))).thenReturn(0);
        when(jdbcTemplate.update(contains("INSERT INTO tb_capsule_usage_quota"),
                any(Object.class), any(Object.class), any(Object.class)))
                .thenThrow(new DuplicateKeyException("dup"));

        PersonaChatMessage result = service.reply(userId, sessionId, "over the limit");

        verify(structuredAiService, never()).call(any(), any(), any(), any(), any(), any());
        assertTrue(result.textContent.contains("慢信"));

        ArgumentCaptor<PersonaChatSession> cap = ArgumentCaptor.forClass(PersonaChatSession.class);
        verify(sessionMapper).updateById(cap.capture());
        assertEquals("LETTER_GUIDED", cap.getValue().status);
    }

    @Test
    @DisplayName("MAJOR-2: over-limit does NOT persist the visitor message")
    void overLimit_doesNotInsertVisitorMessage() {
        Long userId = 4L, sessionId = 13L, capsuleId = 103L;
        EchoCapsule c = capsule(capsuleId);
        when(sessionMapper.selectById(sessionId)).thenReturn(session(sessionId, userId, capsuleId));
        when(safetyService.check(any(), any(), any())).thenReturn(safePassed());
        when(capsuleMapper.selectById(capsuleId)).thenReturn(c);
        when(jdbcTemplate.update(contains("UPDATE tb_capsule_usage_quota SET turn_count = turn_count + 1"),
                any(Object.class), any(Object.class), any(Object.class), any(Object.class))).thenReturn(0);
        when(jdbcTemplate.update(contains("INSERT INTO tb_capsule_usage_quota"),
                any(Object.class), any(Object.class), any(Object.class)))
                .thenThrow(new DuplicateKeyException("dup"));

        service.reply(userId, sessionId, "over the limit msg");

        // Only the capsule (guidance) message is inserted — never the VISITOR message.
        ArgumentCaptor<PersonaChatMessage> cap = ArgumentCaptor.forClass(PersonaChatMessage.class);
        verify(messageMapper, atLeastOnce()).insert(cap.capture());
        assertTrue(cap.getAllValues().stream().noneMatch(m -> "VISITOR".equals(m.senderType)),
                "over-limit turn must NOT persist a VISITOR message");
    }

    @Test
    @DisplayName("MAJOR-3: first-day insert collision is resolved by a retry UPDATE (reserves, returns true)")
    void firstDayInsertCollision_resolvesViaUpdate() {
        Long userId = 5L, sessionId = 14L, capsuleId = 104L;
        EchoCapsule c = capsule(capsuleId);
        when(sessionMapper.selectById(sessionId)).thenReturn(session(sessionId, userId, capsuleId));
        when(safetyService.check(any(), any(), any())).thenReturn(safePassed());
        when(capsuleMapper.selectById(capsuleId)).thenReturn(c);
        // First UPDATE: 0 (no row). INSERT: DuplicateKey (concurrent first-insert won).
        // Retry UPDATE: 1 (row now exists, under limit) → reserved.
        when(jdbcTemplate.update(contains("UPDATE tb_capsule_usage_quota SET turn_count = turn_count + 1"),
                any(Object.class), any(Object.class), any(Object.class), any(Object.class)))
                .thenReturn(0)   // first attempt
                .thenReturn(1);  // retry after collision
        when(jdbcTemplate.update(contains("INSERT INTO tb_capsule_usage_quota"),
                any(Object.class), any(Object.class), any(Object.class)))
                .thenThrow(new DuplicateKeyException("dup"));
        when(agentContextAssembler.assemble(any(), any(), any(), anyBoolean())).thenReturn(new AgentContext());
        when(structuredAiService.call(any(), any(), any(), any(), any(), any())).thenReturn(goodReply());
        when(boundaryMapper.selectOne(any())).thenReturn(null);

        PersonaChatMessage result = service.reply(userId, sessionId, "first message of the day");

        // Reserved via the retry UPDATE → AI is called, not letter-guided.
        verify(structuredAiService).call(any(), any(), any(), any(), any(), any());
        assertFalse(result.textContent.contains("慢信"));
        // The conditional UPDATE ran twice (initial + retry).
        verify(jdbcTemplate, times(2)).update(contains("UPDATE tb_capsule_usage_quota SET turn_count = turn_count + 1"),
                any(Object.class), any(Object.class), any(Object.class), any(Object.class));
    }
}
