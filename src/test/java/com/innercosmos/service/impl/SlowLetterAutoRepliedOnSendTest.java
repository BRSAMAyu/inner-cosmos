package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.ai.agent.LetterGuardAgent;
import com.innercosmos.entity.LetterStatusLog;
import com.innercosmos.entity.SlowLetter;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.letterstate.LetterStateRegistry;
import com.innercosmos.mapper.BlockRelationMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
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
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Gemini audit 1.8 (CONFIRMED/P1): the parent letter's REPLIED transition used to depend on the
 * frontend making a SECOND, disconnected call (POST /{id}/reply) any time after a reply draft
 * was created -- with no guarantee the reply had actually been sent, or even that it still
 * existed. These tests pin the fix: REPLIED is no longer an externally settable target at all,
 * and instead happens ONLY as an atomic side effect, in the SAME transaction, of a linked reply
 * letter's own successful SENT transition. They also pin that a retried "send" is idempotent
 * rather than erroring or re-sending.
 */
@ExtendWith(MockitoExtension.class)
class SlowLetterAutoRepliedOnSendTest {

    @Mock private SlowLetterMapper letterMapper;
    @Mock private LetterStatusLogMapper logMapper;
    @Mock private LetterStateRegistry stateRegistry;
    @Mock private LetterGuardAgent guardAgent;
    @Mock private LetterThreadMapper threadMapper;
    @Mock private ReportRecordMapper reportRecordMapper;
    @Mock private LetterSafetyFilter letterSafetyFilter;
    @Mock private EchoCapsuleMapper capsuleMapper;
    @Mock private BlockRelationMapper blockRelationMapper;

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-07-23T08:00:00Z"), ZoneOffset.UTC);

    private SlowLetterServiceImpl service() {
        return new SlowLetterServiceImpl(letterMapper, logMapper, stateRegistry, guardAgent,
                threadMapper, reportRecordMapper, letterSafetyFilter, capsuleMapper, blockRelationMapper,
                FIXED_CLOCK);
    }

    private com.innercosmos.service.LetterSafetyFilter.FilterResult passed() {
        com.innercosmos.service.LetterSafetyFilter.FilterResult r = new com.innercosmos.service.LetterSafetyFilter.FilterResult();
        r.passed = true;
        return r;
    }

    private SlowLetter draftReply(long id, long senderId, long receiverId, long replyToLetterId) {
        SlowLetter reply = new SlowLetter();
        reply.id = id;
        reply.senderUserId = senderId;
        reply.receiverUserId = receiverId;
        reply.status = "DRAFT";
        reply.replyToLetterId = replyToLetterId;
        return reply;
    }

    // ── REPLIED can never be a client-chosen target ────────────────────────────────

    @Test
    @DisplayName("1.8: REPLIED can no longer be manually set via transition() -- BAD_REQUEST, no write")
    void transition_toReplied_rejectedAsClientChosenTarget() {
        SlowLetter letter = new SlowLetter();
        letter.id = 1L;
        letter.senderUserId = 10L;
        letter.receiverUserId = 20L;
        letter.status = "READ";
        when(letterMapper.selectById(1L)).thenReturn(letter);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().transition(20L, 1L, "REPLIED"));

        assertEquals("BAD_REQUEST", ex.code);
        verify(letterMapper, never()).update(any(), any());
    }

    // ── atomic auto-REPLIED on a linked reply's SENT ───────────────────────────────

    @Test
    @DisplayName("1.8: sending a reply (replyToLetterId set) atomically flips the READ original to REPLIED in the SAME call")
    void transition_replySent_atomicallyFlipsOriginalToReplied() {
        long originalId = 5L;
        SlowLetter reply = draftReply(6L, 20L, 10L, originalId);
        when(letterMapper.selectById(6L)).thenReturn(reply);
        when(letterSafetyFilter.filter(any(), any(), any())).thenReturn(passed());
        // First update() call = the reply's own DRAFT->SENT advance; second = the original's
        // conditional READ->REPLIED flip. Both must succeed for this test's happy path.
        when(letterMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);

        SlowLetter result = service().transition(20L, 6L, "SENT");

        assertEquals("SENT", result.status);
        ArgumentCaptor<UpdateWrapper> captor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(letterMapper, times(2)).update(any(), captor.capture());
        String originalFlipWhere = captor.getAllValues().get(1).getSqlSegment();
        org.junit.jupiter.api.Assertions.assertTrue(originalFlipWhere.contains("status"),
                "the original's flip must be gated on its OWN status (READ), not blindly applied");

        // A status-log row must record the auto-transition on the ORIGINAL letter, not just the
        // reply's own SENT transition.
        ArgumentCaptor<LetterStatusLog> logCaptor = ArgumentCaptor.forClass(LetterStatusLog.class);
        verify(logMapper, times(2)).insert(logCaptor.capture());
        boolean originalRepliedLogged = logCaptor.getAllValues().stream()
                .anyMatch(log -> originalId == log.letterId && "REPLIED".equals(log.toStatus));
        org.junit.jupiter.api.Assertions.assertTrue(originalRepliedLogged,
                "must log the original's automatic READ->REPLIED transition");
    }

    @Test
    @DisplayName("1.8: sending a reply still succeeds even when the original is no longer READ (already REPLIED/BLOCKED/etc) -- best-effort flip, not a hard dependency")
    void transition_replySent_originalNotReadAnymore_stillSendsReplySuccessfully() {
        long originalId = 7L;
        SlowLetter reply = draftReply(8L, 20L, 10L, originalId);
        when(letterMapper.selectById(8L)).thenReturn(reply);
        when(letterSafetyFilter.filter(any(), any(), any())).thenReturn(passed());
        // The reply's own SENT advance succeeds (1 row); the original's conditional flip affects
        // 0 rows because the original is no longer READ (e.g. a different reply already flipped
        // it, or the receiver blocked/declined in the meantime).
        when(letterMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1).thenReturn(0);

        SlowLetter result = service().transition(20L, 8L, "SENT");

        assertEquals("SENT", result.status, "the reply must still send successfully");
        // Only ONE status-log insert -- the reply's own SENT transition. No log for a flip that
        // never actually happened.
        verify(logMapper, times(1)).insert(any(LetterStatusLog.class));
    }

    // ── idempotent send ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("1.8: retrying an already-SENT letter's send is idempotent -- returns the same letter, no re-validation or double side effects")
    void transition_sendAlreadySent_isIdempotentNoOp() {
        SlowLetter letter = new SlowLetter();
        letter.id = 9L;
        letter.senderUserId = 20L;
        letter.receiverUserId = 10L;
        letter.status = "SENT"; // already sent by an earlier, successful call
        when(letterMapper.selectById(9L)).thenReturn(letter);

        SlowLetter result = service().transition(20L, 9L, "SENT");

        assertEquals("SENT", result.status);
        // A retried send must not re-run safety checks, attempt another write, or log again.
        verify(letterSafetyFilter, never()).filter(any(), any(), any());
        verify(letterMapper, never()).update(any(), any());
        verify(logMapper, never()).insert(any(LetterStatusLog.class));
    }
}
