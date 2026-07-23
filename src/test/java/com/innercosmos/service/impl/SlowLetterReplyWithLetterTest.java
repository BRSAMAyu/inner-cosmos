package com.innercosmos.service.impl;

import com.innercosmos.ai.agent.LetterGuardAgent;
import com.innercosmos.dto.LetterCreateRequest;
import com.innercosmos.entity.LetterThread;
import com.innercosmos.entity.SlowLetter;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.exception.SafetyBlockedException;
import com.innercosmos.letterstate.LetterStateRegistry;
import com.innercosmos.safety.PiiCredentialDetector;
import com.innercosmos.mapper.LetterStatusLogMapper;
import com.innercosmos.mapper.LetterThreadMapper;
import com.innercosmos.mapper.ReportRecordMapper;
import com.innercosmos.mapper.SlowLetterMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.BlockRelationMapper;
import com.innercosmos.service.LetterSafetyFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IC-LTR-001: pins the contract of {@link SlowLetterServiceImpl#replyWithLetter}
 * (slow-letter #33 — "reply with a letter of your own"). The frontend feature
 * newly calling this path must NEVER bypass the {@link LetterGuardAgent} safety
 * gate or the receiver-only authorization, so this test binds both.
 */
@ExtendWith(MockitoExtension.class)
class SlowLetterReplyWithLetterTest {

    @Mock
    private SlowLetterMapper letterMapper;
    @Mock
    private LetterStatusLogMapper logMapper;
    @Mock
    private LetterStateRegistry stateRegistry;
    @Mock
    private LetterGuardAgent guardAgent;
    @Mock
    private LetterThreadMapper threadMapper;
    @Mock
    private ReportRecordMapper reportRecordMapper;
    @Mock
    private LetterSafetyFilter letterSafetyFilter;
    @Mock
    private EchoCapsuleMapper capsuleMapper;
    @Mock
    private BlockRelationMapper blockRelationMapper;

    private SlowLetterServiceImpl service;

    // Constructed per-test so lenient stubs don't leak across cases.
    private SlowLetterServiceImpl newService() {
        LetterSafetyFilter.FilterResult safe = new LetterSafetyFilter.FilterResult();
        safe.passed = true;
        org.mockito.Mockito.lenient().when(letterSafetyFilter.filter(any(), any(), any())).thenReturn(safe);
        return new SlowLetterServiceImpl(letterMapper, logMapper, stateRegistry,
                guardAgent, threadMapper, reportRecordMapper, letterSafetyFilter, capsuleMapper, blockRelationMapper, new PiiCredentialDetector(),
                Clock.systemUTC());
    }

    /** An original letter sent by `sender` to `receiver`. */
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
    @DisplayName("happy path: receiver replies with a new DRAFT letter + an ACTIVE thread linking both parties")
    void replyWithLetter_happyPath_createsDraftAndThread() {
        service = newService();
        long sender = 10L;
        long receiver = 20L;
        SlowLetter original = original(1L, sender, receiver, 77L);
        when(letterMapper.selectById(1L)).thenReturn(original);
        when(guardAgent.allow(replyRequest().letterBody)).thenReturn(true);

        SlowLetter reply = service.replyWithLetter(receiver, 1L, replyRequest());

        // Reply letter: sender = the replying receiver, receiver = the original sender.
        ArgumentCaptor<SlowLetter> replyCap = ArgumentCaptor.forClass(SlowLetter.class);
        verify(letterMapper).insert(replyCap.capture());
        SlowLetter inserted = replyCap.getValue();
        assertEquals(receiver, inserted.senderUserId, "reply sender must be the replying user");
        assertEquals(sender, inserted.receiverUserId, "reply receiver must be the original sender");
        assertEquals("DRAFT", inserted.status, "reply is created as a DRAFT for the user to review");

        // Thread: participantA = original sender, participantB = replying receiver.
        ArgumentCaptor<LetterThread> threadCap = ArgumentCaptor.forClass(LetterThread.class);
        verify(threadMapper).insert(threadCap.capture());
        LetterThread thread = threadCap.getValue();
        assertEquals(1L, thread.firstLetterId, "thread anchors on the original letter");
        assertEquals(sender, thread.participantA, "participantA is the original sender");
        assertEquals(receiver, thread.participantB, "participantB is the replying receiver");
        assertEquals("ACTIVE", thread.status);

        // The returned object is the persisted reply (same shape as inserted).
        assertEquals(receiver, reply.senderUserId);
        assertEquals(sender, reply.receiverUserId);
    }

    @Test
    @DisplayName("authorization: a non-receiver caller is rejected (BusinessException) before any write")
    void replyWithLetter_nonReceiver_throwsUnauthorized() {
        service = newService();
        SlowLetter original = original(1L, 10L, 20L, 77L); // addressed to user 20
        when(letterMapper.selectById(1L)).thenReturn(original);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.replyWithLetter(99L, 1L, replyRequest())); // 99 is neither party's intended receiver

        assertEquals("UNAUTHORIZED", ex.code, "only the original receiver may reply with a letter");
        verify(letterMapper, never()).insert(any(SlowLetter.class));
        verify(threadMapper, never()).insert(any(LetterThread.class));
    }

    @Test
    @DisplayName("safety: when the guard blocks the body, NO reply and NO thread are persisted")
    void replyWithLetter_unsafeBody_blockedWithNoInserts() {
        service = newService();
        SlowLetter original = original(1L, 10L, 20L, 77L);
        when(letterMapper.selectById(1L)).thenReturn(original);
        when(guardAgent.allow(replyRequest().letterBody)).thenReturn(false);

        assertThrows(SafetyBlockedException.class,
                () -> service.replyWithLetter(20L, 1L, replyRequest()));

        // Binding safety regression: the new frontend feature can never bypass the guard.
        verify(letterMapper, never()).insert(any(SlowLetter.class));
        verify(threadMapper, never()).insert(any(LetterThread.class));
    }
}
