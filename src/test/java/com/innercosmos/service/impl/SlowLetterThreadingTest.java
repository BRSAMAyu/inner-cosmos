package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.agent.LetterGuardAgent;
import com.innercosmos.dto.LetterCreateRequest;
import com.innercosmos.entity.EchoCapsule;
import com.innercosmos.entity.BlockRelation;
import com.innercosmos.entity.LetterThread;
import com.innercosmos.entity.SlowLetter;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.letterstate.LetterStateRegistry;
import com.innercosmos.safety.PiiCredentialDetector;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.BlockRelationMapper;
import com.innercosmos.mapper.LetterStatusLogMapper;
import com.innercosmos.mapper.LetterThreadMapper;
import com.innercosmos.mapper.ReportRecordMapper;
import com.innercosmos.mapper.SlowLetterMapper;
import com.innercosmos.service.LetterSafetyFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IC-LTR-001: pins the conversation-threading contract of {@link SlowLetterServiceImpl}.
 * Replies must JOIN an existing thread (order-independent participant pair + capsule)
 * instead of orphaning a fresh thread each round, and a thread's letters must be
 * walkable only by its two participants.
 */
@ExtendWith(MockitoExtension.class)
class SlowLetterThreadingTest {

    @Mock private SlowLetterMapper letterMapper;
    @Mock private LetterStatusLogMapper logMapper;
    @Mock private LetterStateRegistry stateRegistry;
    @Mock private LetterGuardAgent guardAgent;
    @Mock private LetterThreadMapper threadMapper;
    @Mock private ReportRecordMapper reportRecordMapper;
    @Mock private LetterSafetyFilter letterSafetyFilter;
    @Mock private EchoCapsuleMapper capsuleMapper;
    @Mock private BlockRelationMapper blockRelationMapper;

    private SlowLetterServiceImpl newService() {
        LetterSafetyFilter.FilterResult safe = new LetterSafetyFilter.FilterResult();
        safe.passed = true;
        org.mockito.Mockito.lenient().when(letterSafetyFilter.filter(any(), any(), any())).thenReturn(safe);
        return new SlowLetterServiceImpl(letterMapper, logMapper, stateRegistry,
                guardAgent, threadMapper, reportRecordMapper, letterSafetyFilter, capsuleMapper, blockRelationMapper, new PiiCredentialDetector(),
                Clock.systemUTC());
    }

    private SlowLetter original(long id, long sender, long receiver, long capsuleId) {
        SlowLetter letter = new SlowLetter();
        letter.id = id;
        letter.senderUserId = sender;
        letter.receiverUserId = receiver;
        letter.receiverCapsuleId = capsuleId;
        letter.title = "一封来自远方的信";
        letter.status = "READ";
        return letter;
    }

    private LetterCreateRequest replyRequest() {
        LetterCreateRequest request = new LetterCreateRequest();
        request.receiverUserId = 10L;
        request.receiverCapsuleId = 77L;
        request.title = "谢谢你愿意写下这些";
        request.letterBody = "我在第三段停了下来，想慢慢回你一封信。";
        return request;
    }

    @Test
    @DisplayName("draft: a public capsule resolves its owner server-side without exposing the owner id")
    void draftToPublicCapsule_resolvesReceiverOwner() {
        EchoCapsule capsule = new EchoCapsule();
        capsule.id = 77L;
        capsule.ownerUserId = 20L;
        capsule.isPublic = true;
        capsule.visibilityStatus = "PUBLIC";
        when(capsuleMapper.selectById(77L)).thenReturn(capsule);
        when(guardAgent.allow(any())).thenReturn(true);
        LetterCreateRequest request = replyRequest();
        request.receiverUserId = null;

        SlowLetter created = newService().draft(10L, request);

        assertEquals(20L, created.receiverUserId);
        ArgumentCaptor<SlowLetter> inserted = ArgumentCaptor.forClass(SlowLetter.class);
        verify(letterMapper).insert(inserted.capture());
        assertEquals(20L, inserted.getValue().receiverUserId);
    }

    @Test
    @DisplayName("draft: a client cannot redirect a public capsule letter to a different owner")
    void draftToPublicCapsule_rejectsForgedReceiver() {
        EchoCapsule capsule = new EchoCapsule();
        capsule.id = 77L;
        capsule.ownerUserId = 20L;
        capsule.isPublic = true;
        capsule.visibilityStatus = "PUBLIC";
        when(capsuleMapper.selectById(77L)).thenReturn(capsule);
        when(guardAgent.allow(any())).thenReturn(true);
        LetterCreateRequest request = replyRequest();
        request.receiverUserId = 99L;

        BusinessException ex = assertThrows(BusinessException.class,
                () -> newService().draft(10L, request));

        assertEquals("BAD_REQUEST", ex.code);
        verify(letterMapper, never()).insert(any(SlowLetter.class));
    }

    @Test
    @DisplayName("draft: an official seed without a human owner can never become a real-person route")
    void draftToOwnerlessSeed_isNeverARealPersonRoute() {
        EchoCapsule capsule = new EchoCapsule();
        capsule.id = 77L;
        capsule.ownerUserId = null;
        capsule.isPublic = true;
        capsule.visibilityStatus = "PUBLIC";
        when(capsuleMapper.selectById(77L)).thenReturn(capsule);
        when(guardAgent.allow(any())).thenReturn(true);
        LetterCreateRequest request = replyRequest();
        request.receiverUserId = 99L;

        BusinessException ex = assertThrows(BusinessException.class,
                () -> newService().draft(10L, request));

        assertEquals("NOT_FOUND", ex.code);
        verify(letterMapper, never()).insert(any(SlowLetter.class));
    }

    @Test
    @DisplayName("block: a receiver blocking a letter also blocks future delivery from that sender")
    void blockByReceiver_persistsRelationshipBoundary() {
        SlowLetter letter = original(1L, 10L, 20L, 77L);
        letter.status = "DELIVERED";
        when(letterMapper.selectById(1L)).thenReturn(letter);
        when(letterMapper.update(any(), any())).thenReturn(1);
        when(blockRelationMapper.selectCount(any())).thenReturn(0L);

        SlowLetter blocked = newService().transition(20L, 1L, "BLOCKED");

        assertEquals("BLOCKED", blocked.status);
        ArgumentCaptor<BlockRelation> relation = ArgumentCaptor.forClass(BlockRelation.class);
        verify(blockRelationMapper).insert(relation.capture());
        assertEquals(20L, relation.getValue().blockerUserId);
        assertEquals(10L, relation.getValue().blockedUserId);
    }

    @Test
    @DisplayName("inbox: letters still travelling are filtered before their body reaches the receiver")
    void inbox_excludesPreArrivalStatesAtTheQueryBoundary() {
        when(letterMapper.selectList(any())).thenReturn(List.of());

        newService().inbox(20L);

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<QueryWrapper> query = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(letterMapper).selectList(query.capture());
        String sql = query.getValue().getSqlSegment();
        assertTrue(sql.contains("status IN"));
        assertTrue(sql.contains("receiver_user_id"));
    }

    @Test
    @DisplayName("transition: users cannot impersonate delivery or sender/receiver-only actions")
    void transition_enforcesActorAndSystemBoundaries() {
        SlowLetter letter = original(1L, 10L, 20L, 77L);
        letter.status = "FLYING";
        when(letterMapper.selectById(1L)).thenReturn(letter);

        assertEquals("UNAUTHORIZED", assertThrows(BusinessException.class,
                () -> newService().transition(10L, 1L, "DELIVERED")).code);
        assertEquals("UNAUTHORIZED", assertThrows(BusinessException.class,
                () -> newService().transition(10L, 1L, "READ")).code);
        verify(letterMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("reuse: when the original already belongs to a thread, the reply JOINS it — no new thread is inserted")
    void replyWithLetter_originalHasThread_reusesIt() {
        SlowLetterServiceImpl service = newService();
        long sender = 10L, receiver = 20L;
        SlowLetter original = original(1L, sender, receiver, 77L);
        original.threadId = 555L;
        LetterThread existing = new LetterThread();
        existing.id = 555L;
        existing.participantA = sender;
        existing.participantB = receiver;
        existing.capsuleId = 77L;
        existing.status = "ACTIVE";
        when(letterMapper.selectById(1L)).thenReturn(original);
        when(threadMapper.selectById(555L)).thenReturn(existing);
        when(guardAgent.allow(replyRequest().letterBody)).thenReturn(true);

        SlowLetter reply = service.replyWithLetter(receiver, 1L, replyRequest());

        // No fresh thread row.
        verify(threadMapper, never()).insert(any(LetterThread.class));
        // Reply is stamped with the existing thread id.
        ArgumentCaptor<SlowLetter> cap = ArgumentCaptor.forClass(SlowLetter.class);
        verify(letterMapper).insert(cap.capture());
        assertEquals(555L, cap.getValue().threadId, "reply must carry the existing thread id");
        assertEquals(555L, reply.threadId);
        // Thread freshness is bumped.
        verify(threadMapper).updateById(any(LetterThread.class));
    }

    @Test
    @DisplayName("reuse: an existing thread is matched by order-independent participant pair + capsule even when threadId is unset")
    void replyWithLetter_matchesThreadByParticipantsAndCapsule() {
        SlowLetterServiceImpl service = newService();
        long sender = 10L, receiver = 20L;
        SlowLetter original = original(1L, sender, receiver, 77L); // threadId null
        LetterThread existing = new LetterThread();
        existing.id = 900L;
        existing.participantA = receiver; // stored in the OTHER order
        existing.participantB = sender;
        existing.capsuleId = 77L;
        when(letterMapper.selectById(1L)).thenReturn(original);
        when(threadMapper.selectList(any())).thenReturn(List.of(existing));
        when(guardAgent.allow(replyRequest().letterBody)).thenReturn(true);

        SlowLetter reply = service.replyWithLetter(receiver, 1L, replyRequest());

        verify(threadMapper, never()).insert(any(LetterThread.class));
        assertEquals(900L, reply.threadId, "reply joins the order-independent matched thread");
        // The original is back-filled with the thread id so the whole conversation is walkable.
        verify(letterMapper).updateById(any(SlowLetter.class));
    }

    @Test
    @DisplayName("getThreadLetters: a participant receives the conversation; a stranger is rejected")
    void getThreadLetters_ownershipEnforced() {
        SlowLetterServiceImpl service = newService();
        LetterThread thread = new LetterThread();
        thread.id = 42L;
        thread.firstLetterId = 1L;
        thread.participantA = 10L;
        thread.participantB = 20L;
        when(threadMapper.selectById(42L)).thenReturn(thread);
        when(letterMapper.selectList(any())).thenReturn(List.of(original(1L, 10L, 20L, 77L)));

        List<SlowLetter> letters = service.getThreadLetters(10L, 42L);
        assertNotNull(letters);
        assertEquals(1, letters.size());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getThreadLetters(99L, 42L));
        assertEquals("UNAUTHORIZED", ex.code, "only the two participants may read a thread");
    }

    @Test
    @DisplayName("requestRewrite: a letter that PASSES safety still gets a gentle, non-null suggestion")
    void requestRewrite_passing_returnsGentleSuggestion() {
        SlowLetterServiceImpl service = newService();
        SlowLetter letter = original(1L, 10L, 20L, 77L);
        letter.letterBody = "我想谢谢你。";
        when(letterMapper.selectById(1L)).thenReturn(letter);
        LetterSafetyFilter.FilterResult passed = new LetterSafetyFilter.FilterResult();
        passed.passed = true;
        when(letterSafetyFilter.filter(any(), any(), any())).thenReturn(passed);

        String suggestion = service.requestRewrite(10L, 1L);

        assertNotNull(suggestion, "rewrite coaching must never be null when the letter is safe");
        assertTrue(suggestion.length() > 0);
    }
}
