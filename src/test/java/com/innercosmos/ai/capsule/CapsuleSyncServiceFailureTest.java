package com.innercosmos.ai.capsule;

import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.ai.portrait.AgentUserRelationshipService;
import com.innercosmos.ai.portrait.UserPortraitService;
import com.innercosmos.entity.CapsuleSyncQueue;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.mapper.CapsuleSyncQueueMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.UserLongTermMemoryMapper;
import com.innercosmos.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * IC-CAP-002 B-2: when regeneration throws, the queue row must become FAILED with
 * attemptCount incremented and a SYNC_FAILED notification raised (failure is visible,
 * not swallowed).
 */
@ExtendWith(MockitoExtension.class)
class CapsuleSyncServiceFailureTest {

    @Mock private PiiPrivacyFilter piiFilter;
    @Mock private CapsuleContextRegenerator regenerator;
    @Mock private EchoCapsuleMapper capsuleMapper;
    @Mock private CapsuleSyncQueueMapper syncQueueMapper;
    @Mock private UserLongTermMemoryMapper ltmMapper;
    @Mock private UserPortraitService portraitService;
    @Mock private AgentUserRelationshipService relationshipService;
    @Mock private NotificationService notificationService;

    private CapsuleSyncService service;

    @BeforeEach
    void setUp() {
        service = new CapsuleSyncService(piiFilter, regenerator, capsuleMapper,
                syncQueueMapper, ltmMapper, portraitService, relationshipService,
                notificationService);
    }

    private PiiPrivacyFilter.FilteredPortrait portrait() {
        // FilteredPortrait is a record — build a real instance.
        return new PiiPrivacyFilter.FilteredPortrait(
                "TA同学", "上海", "25-30", "互联网/技术",
                List.of("真实"), null, List.of("倾听者"), List.of());
    }

    private CapsuleSyncQueue queue(Long id, Long userId, Long capsuleId) {
        CapsuleSyncQueue q = new CapsuleSyncQueue();
        q.id = id;
        q.userId = userId;
        q.capsuleId = capsuleId;
        q.status = "APPROVED";
        q.attemptCount = 0;
        return q;
    }

    @Test
    @DisplayName("B-2: regeneration failure marks the queue row FAILED + notifies")
    void asyncRegenerate_failure_marksFailedAndNotifies() {
        CapsuleSyncQueue q = queue(7L, 1L, 100L);
        doThrow(new RuntimeException("llm down"))
                .when(regenerator).regenerate(eq(100L), any(), any());

        service.regenerateOne(q, portrait(), List.of("theme"));

        assertEquals("FAILED", q.status);
        assertEquals(1, q.attemptCount);
        assertNotNull(q.lastError);
        assertNotNull(q.failedAt);
        assertNotNull(q.nextRetryAt);
        verify(syncQueueMapper).updateById(any(CapsuleSyncQueue.class));
        verify(notificationService, times(1))
                .notify(eq(1L), eq("SYNC_FAILED"), anyString(), anyString(), eq(7L), eq("CAPSULE_SYNC"));
        verify(notificationService, never())
                .notify(any(), eq("SYNC_DONE"), anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("B-2: successful regeneration marks the queue row SYNCED + notifies done")
    void regenerateOne_success_marksSyncedAndNotifies() {
        CapsuleSyncQueue q = queue(8L, 2L, 200L);
        // regenerate() does nothing (success)

        service.regenerateOne(q, portrait(), List.of("theme"));

        assertEquals("SYNCED", q.status);
        verify(notificationService, times(1))
                .notify(eq(2L), eq("SYNC_DONE"), anyString(), anyString(), eq(8L), eq("CAPSULE_SYNC"));
    }

    @Test
    @DisplayName("B-2: retryFailed gives up once attemptCount hits MAX_ATTEMPTS")
    void retryFailed_stopsAtMaxAttempts() {
        CapsuleSyncQueue exhausted = queue(9L, 3L, 300L);
        exhausted.status = "FAILED";
        exhausted.attemptCount = CapsuleSyncService.MAX_ATTEMPTS;
        when(syncQueueMapper.selectById(9L)).thenReturn(exhausted);

        service.retryFailed(9L);

        // No regen attempt, no further bookkeeping write, no notification.
        verify(regenerator, never()).regenerate(any(), any(), any());
        verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any());
    }

    // ---------------------------------------------------------------------------------
    // IC-CAP-002 FIX-1 (IDOR): the manual /retry endpoint routes through the user-scoped
    // overload retryFailed(userId, queueId). User B must NOT be able to trigger LLM
    // regeneration of user A's FAILED row. Before the fix retryFailed took only queueId,
    // so ANY logged-in user could burn LLM resources on (and misdirect a notification for)
    // another user's row.
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("FIX-1: user B cannot retry user A's FAILED row (UNAUTHORIZED, no regeneration fired)")
    void retry_rejectsOtherUsersQueueRow() {
        // Row 50 is owned by user A (1L). User B (2L) attempts the retry.
        CapsuleSyncQueue rowOfA = queue(50L, 1L, 500L);
        rowOfA.status = "FAILED";
        rowOfA.attemptCount = 1;
        when(syncQueueMapper.selectById(50L)).thenReturn(rowOfA);

        com.innercosmos.exception.BusinessException ex = assertThrows(
                com.innercosmos.exception.BusinessException.class,
                () -> service.retryFailed(2L, 50L),
                "user B retrying user A's row must throw");
        assertEquals(com.innercosmos.common.ErrorCode.UNAUTHORIZED, ex.code,
                "ownership mismatch must surface as UNAUTHORIZED");

        // CRITICAL: no LLM regeneration, no portrait rebuild, no notification — the resource
        // burn and misdirected notification are exactly what this hole leaked.
        verify(regenerator, never()).regenerate(any(), any(), any());
        verify(piiFilter, never()).filter(any(), any());
        verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any());
        verify(syncQueueMapper, never()).updateById(any(CapsuleSyncQueue.class));
    }

    @Test
    @DisplayName("FIX-1: the owner can still retry their own FAILED row (regeneration runs)")
    void retry_ownerSucceeds() {
        CapsuleSyncQueue rowOfA = queue(51L, 1L, 501L);
        rowOfA.status = "FAILED";
        rowOfA.attemptCount = 1;
        when(syncQueueMapper.selectById(51L)).thenReturn(rowOfA);
        when(ltmMapper.selectList(any())).thenReturn(List.of());
        // createSnapshot returns null (its value only flows into filter, which we stub); filter
        // yields a concrete FilteredPortrait so regenerate() receives valid input.
        when(piiFilter.filter(any(), any())).thenReturn(portrait());

        service.retryFailed(1L, 51L);

        // Owner path: regeneration runs and the row is updated to SYNCED (regenerator is a no-op mock).
        verify(regenerator, times(1)).regenerate(eq(501L), any(), any());
        assertEquals("SYNCED", rowOfA.status);
    }

    // ---------------------------------------------------------------------------------
    // IC-CAP-002 FIX-1: the above tests mock the regenerator. These two drive the REAL
    // CapsuleContextRegenerator (only LlmClient + EchoCapsuleMapper are mocked) to prove
    // that a failing/empty LLM PROPAGATES out of regenerate() and surfaces as FAILED +
    // SYNC_FAILED end-to-end — closing the swallow gap the reviewer flagged. Before the fix
    // the regenerator caught the exception / treated blank output as a no-op and returned
    // normally, so regenerateOne reported SYNCED + SYNC_DONE (these assertions would fail).
    // ---------------------------------------------------------------------------------

    private CapsuleSyncService serviceWithRealRegenerator(LlmClient llmClient,
                                                          EchoCapsuleMapper realCapsuleMapper) {
        CapsuleContextRegenerator realRegenerator =
                new CapsuleContextRegenerator(llmClient, realCapsuleMapper);
        return new CapsuleSyncService(piiFilter, realRegenerator, realCapsuleMapper,
                syncQueueMapper, ltmMapper, portraitService, relationshipService,
                notificationService);
    }

    private EchoCapsule existingCapsule(Long id, Long ownerUserId) {
        EchoCapsule c = new EchoCapsule();
        c.id = id;
        c.ownerUserId = ownerUserId;
        c.pseudonym = "TA同学";
        c.intro = "一枚数字回声";
        return c;
    }

    @Test
    @DisplayName("FIX-1: REAL regenerator with a throwing LlmClient -> FAILED + SYNC_FAILED, no SYNC_DONE")
    void realRegenerator_llmThrows_surfacesAsFailed() {
        LlmClient llmClient = mock(LlmClient.class);
        EchoCapsuleMapper realCapsuleMapper = mock(EchoCapsuleMapper.class);
        when(realCapsuleMapper.selectById(100L)).thenReturn(existingCapsule(100L, 1L));
        when(llmClient.chat(any(LlmRequest.class)))
                .thenThrow(new RuntimeException("llm provider unavailable"));

        CapsuleSyncService svc = serviceWithRealRegenerator(llmClient, realCapsuleMapper);
        CapsuleSyncQueue q = queue(7L, 1L, 100L);

        svc.regenerateOne(q, portrait(), List.of("theme"));

        assertEquals("FAILED", q.status, "LLM failure must propagate and mark the row FAILED");
        assertEquals(1, q.attemptCount);
        assertNotNull(q.lastError);
        assertNotNull(q.failedAt);
        assertNotNull(q.nextRetryAt);
        verify(notificationService, times(1))
                .notify(eq(1L), eq("SYNC_FAILED"), anyString(), anyString(), eq(7L), eq("CAPSULE_SYNC"));
        verify(notificationService, never())
                .notify(any(), eq("SYNC_DONE"), anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("FIX-1: REAL regenerator with blank LLM output -> FAILED + SYNC_FAILED, no SYNC_DONE")
    void realRegenerator_llmReturnsBlank_surfacesAsFailed() {
        LlmClient llmClient = mock(LlmClient.class);
        EchoCapsuleMapper realCapsuleMapper = mock(EchoCapsuleMapper.class);
        when(realCapsuleMapper.selectById(101L)).thenReturn(existingCapsule(101L, 5L));
        when(llmClient.chat(any(LlmRequest.class))).thenReturn("   "); // blank == failure

        CapsuleSyncService svc = serviceWithRealRegenerator(llmClient, realCapsuleMapper);
        CapsuleSyncQueue q = queue(11L, 5L, 101L);

        svc.regenerateOne(q, portrait(), List.of("theme"));

        assertEquals("FAILED", q.status, "blank LLM output must be treated as failure, not a silent no-op");
        assertEquals(1, q.attemptCount);
        // The capsule must NOT have been persisted with blank content.
        verify(realCapsuleMapper, never()).updateById(any(EchoCapsule.class));
        verify(notificationService, times(1))
                .notify(eq(5L), eq("SYNC_FAILED"), anyString(), anyString(), eq(11L), eq("CAPSULE_SYNC"));
        verify(notificationService, never())
                .notify(any(), eq("SYNC_DONE"), anyString(), anyString(), any(), anyString());
    }
}
