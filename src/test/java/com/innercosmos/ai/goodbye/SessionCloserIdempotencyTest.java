package com.innercosmos.ai.goodbye;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.portrait.AgentUserRelationshipService;
import com.innercosmos.ai.portrait.PortraitReflectionService;
import com.innercosmos.ai.portrait.UserPortraitService;
import com.innercosmos.ai.proactive.ProactiveEngine;
import com.innercosmos.ai.self.SelfReflectionTrigger;
import com.innercosmos.entity.AgentUserRelationship;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.DialogSession;
import com.innercosmos.mapper.AgentUserRelationshipMapper;
import com.innercosmos.mapper.DialogMessageMapper;
import com.innercosmos.mapper.DialogSessionMapper;
import com.innercosmos.mapper.UserLongTermMemoryMapper;
import com.innercosmos.service.AuroraSelfContinuityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * IC-DATA-002: Unit tests for SessionCloser atomic idempotency guard.
 *
 * Verifies that concurrent / duplicate calls to runAfterGoodbye() do NOT
 * cause double portrait writes or double relationship updates.
 */
@ExtendWith(MockitoExtension.class)
class SessionCloserIdempotencyTest {

    @Mock private SessionSummaryService summarySvc;
    @Mock private PortraitReflectionService portraitSvc;
    @Mock private UserPortraitService portraitMapper;
    @Mock private AgentUserRelationshipService relSvc;
    @Mock private AgentUserRelationshipMapper relMapper;
    @Mock private ProactiveEngine proactiveEngine;
    @Mock private DialogSessionMapper sessionMapper;
    @Mock private DialogMessageMapper messageMapper;
    @Mock private UserLongTermMemoryMapper ltmMapper;
    @Mock private LlmClient llm;
    @Mock private SelfReflectionTrigger selfReflectionTrigger;
    @Mock private AuroraSelfContinuityService continuityService;

    @InjectMocks
    private SessionCloser sessionCloser;

    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 42L;

    @BeforeEach
    void setUp() {
        // Inject optional continuityService via reflection (it's @Autowired(required=false))
        ReflectionTestUtils.setField(sessionCloser, "continuityService", continuityService);
    }

    // ─────────────────────────────────────────────────────────
    // Helper builders
    // ─────────────────────────────────────────────────────────

    private DialogSession openSession() {
        DialogSession s = new DialogSession();
        s.id = SESSION_ID;
        s.userId = USER_ID;
        s.endedAt = null; // not yet closed
        return s;
    }

    private DialogSession closedSession() {
        DialogSession s = new DialogSession();
        s.id = SESSION_ID;
        s.userId = USER_ID;
        s.endedAt = LocalDateTime.now().minusMinutes(1); // already closed
        return s;
    }

    private AgentUserRelationship freshRelationship() {
        AgentUserRelationship rel = new AgentUserRelationship();
        rel.id = 1L;
        rel.userId = USER_ID;
        rel.intimacyLevel = 10;
        rel.trustLevel = 10;
        rel.userDisclosureLevel = 10;
        return rel;
    }

    /** Common stubs for a call that SHOULD do real work (first winner). */
    private void stubForSuccessfulClose() {
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(openSession());
        // Atomic UPDATE returns 1 → this call wins the lock
        when(sessionMapper.update(any(DialogSession.class), any(UpdateWrapper.class)))
                .thenReturn(1);
        when(messageMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(summarySvc.summarize(anyLong(), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(portraitSvc.reflectOnTurn(anyLong(), anyList())).thenReturn(null);
        when(relSvc.getOrInit(anyLong())).thenReturn(freshRelationship());
    }

    // ─────────────────────────────────────────────────────────
    // Test 1: Atomic guard — second concurrent call (sees update=0) must skip
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("IC-DATA-002 Test 1: Second call with update=0 skips portrait write and relationship update")
    void secondCall_atomicUpdateReturnsZero_skipsAllExpensiveWork() {
        // First call: selectById returns open session, update returns 1 (won the lock)
        // Second call: selectById returns open session (endedAt still null in memory mock),
        //              but update returns 0 (DB already closed by first winner)
        when(sessionMapper.selectById(SESSION_ID))
                .thenReturn(openSession())   // first call
                .thenReturn(openSession());  // second call — endedAt still null, but DB wins

        when(sessionMapper.update(any(DialogSession.class), any(UpdateWrapper.class)))
                .thenReturn(1)  // first call wins
                .thenReturn(0); // second call loses (concurrent closer already won)

        when(messageMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(summarySvc.summarize(anyLong(), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(portraitSvc.reflectOnTurn(anyLong(), anyList())).thenReturn(null);
        when(relSvc.getOrInit(anyLong())).thenReturn(freshRelationship());

        // Act: two sequential calls (simulates async race)
        sessionCloser.runAfterGoodbye(USER_ID, SESSION_ID, "LANGUAGE_HIGH");
        sessionCloser.runAfterGoodbye(USER_ID, SESSION_ID, "LANGUAGE_HIGH");

        // Assert: portrait reflect called exactly ONCE (not twice)
        verify(portraitSvc, times(1)).reflectOnTurn(anyLong(), anyList());

        // Assert: relationship getOrInit called exactly ONCE (not twice)
        verify(relSvc, times(1)).getOrInit(USER_ID);

        // Assert: relMapper.updateById called exactly ONCE
        verify(relMapper, times(1)).updateById(any(AgentUserRelationship.class));
    }

    // ─────────────────────────────────────────────────────────
    // Test 2: Early guard — session already closed in DB (endedAt != null)
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("IC-DATA-002 Test 2: Session already closed (endedAt != null) — no work done at all")
    void sessionAlreadyClosed_noWorkDone() {
        // Session fetched already has endedAt set
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(closedSession());

        sessionCloser.runAfterGoodbye(USER_ID, SESSION_ID, "LANGUAGE_HIGH");

        // Assert: atomic UPDATE never called (early return before it)
        verify(sessionMapper, never()).update(any(DialogSession.class), any(UpdateWrapper.class));

        // Assert: no expensive work
        verify(portraitSvc, never()).reflectOnTurn(anyLong(), anyList());
        verify(relSvc, never()).getOrInit(anyLong());
        verify(summarySvc, never()).summarize(anyLong(), anyLong());
    }

    // ─────────────────────────────────────────────────────────
    // Test 3: Session not found — warn and skip
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("IC-DATA-002 Test 3: Session not found — skip all work without error")
    void sessionNotFound_skipsAllWork() {
        when(sessionMapper.selectById(SESSION_ID)).thenReturn(null);

        sessionCloser.runAfterGoodbye(USER_ID, SESSION_ID, "LANGUAGE_HIGH");

        verify(sessionMapper, never()).update(any(DialogSession.class), any(UpdateWrapper.class));
        verify(portraitSvc, never()).reflectOnTurn(anyLong(), anyList());
    }

    // ─────────────────────────────────────────────────────────
    // Test 4: First and only call — does full work and closes session atomically
    // ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("IC-DATA-002 Test 4: Happy path — single call does portrait write + closes session atomically")
    void singleCall_happyPath_doesAllWorkAndClosesSession() {
        stubForSuccessfulClose();

        sessionCloser.runAfterGoodbye(USER_ID, SESSION_ID, "LANGUAGE_HIGH");

        // Portrait work done
        verify(portraitSvc, times(1)).reflectOnTurn(anyLong(), anyList());

        // Relationship updated
        verify(relSvc, times(1)).getOrInit(USER_ID);
        verify(relMapper, times(1)).updateById(any(AgentUserRelationship.class));

        // Atomic close called with an UpdateWrapper (endedAt IS NULL condition)
        verify(sessionMapper, times(1)).update(any(DialogSession.class), any(UpdateWrapper.class));

        // The old pattern (updateById) must NOT be used to close the session
        // (It would be non-atomic). Note: updateById might be called by relMapper
        // which is a different mapper — we only check sessionMapper.updateById is NOT called.
        verify(sessionMapper, never()).updateById(any(DialogSession.class));
    }
}
