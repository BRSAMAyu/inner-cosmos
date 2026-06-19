package com.innercosmos.service.impl;

import com.innercosmos.ai.agent.LetterGuardAgent;
import com.innercosmos.dto.LetterCreateRequest;
import com.innercosmos.entity.LetterThread;
import com.innercosmos.entity.SlowLetter;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.letterstate.LetterStateRegistry;
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

    private SlowLetterServiceImpl newService() {
        return new SlowLetterServiceImpl(letterMapper, logMapper, stateRegistry,
                guardAgent, threadMapper, reportRecordMapper, letterSafetyFilter);
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
