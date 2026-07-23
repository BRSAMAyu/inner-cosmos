package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.ai.agent.LetterGuardAgent;
import com.innercosmos.dto.LetterCreateRequest;
import com.innercosmos.entity.SlowLetter;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.letterstate.LetterStateRegistry;
import com.innercosmos.safety.PiiCredentialDetector;
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
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Gemini audit 1.8 (CONFIRMED/P1): pins two of the three parts of the fix contract:
 *   1. an owner-scoped, version/ETag-checked draft PATCH (SlowLetterServiceImpl#patchDraft) --
 *      IDOR-safe, only while DRAFT, and rejecting a lost concurrent-edit race;
 *   2. an idempotency key on the compose actions (draft/reply-with-letter) so a retried call
 *      returns the original letter instead of inserting a duplicate.
 *
 * (The third part -- the parent letter flipping to REPLIED only as an atomic side effect of the
 * reply actually sending -- is pinned separately in SlowLetterAutoRepliedOnSendTest.)
 */
@ExtendWith(MockitoExtension.class)
class SlowLetterDraftPatchAndIdempotencyTest {

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
                threadMapper, reportRecordMapper, letterSafetyFilter, capsuleMapper, blockRelationMapper, new PiiCredentialDetector(),
                FIXED_CLOCK);
    }

    private SlowLetter draftLetter(long id, long senderId, int version) {
        SlowLetter letter = new SlowLetter();
        letter.id = id;
        letter.senderUserId = senderId;
        letter.receiverUserId = senderId + 1;
        letter.status = "DRAFT";
        letter.title = "old title";
        letter.letterBody = "old body";
        letter.versionNo = version;
        return letter;
    }

    // ── patchDraft: owner scope (IDOR) ──────────────────────────────────────────────

    @Test
    @DisplayName("1.8: a non-owner cannot patch someone else's draft (IDOR guard)")
    void patchDraft_nonOwner_rejected() {
        SlowLetter letter = draftLetter(1L, 10L, 0);
        when(letterMapper.selectById(1L)).thenReturn(letter);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().patchDraft(99L, 1L, "new title", "new body", 0));

        assertEquals("UNAUTHORIZED", ex.code);
        verify(letterMapper, never()).update(any(), any());
    }

    // ── patchDraft: only while DRAFT ────────────────────────────────────────────────

    @Test
    @DisplayName("1.8: a letter that is no longer DRAFT cannot be patched")
    void patchDraft_notDraft_rejected() {
        SlowLetter letter = draftLetter(2L, 10L, 0);
        letter.status = "SENT";
        when(letterMapper.selectById(2L)).thenReturn(letter);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().patchDraft(10L, 2L, "new title", "new body", 0));

        assertEquals("LETTER_STATE_INVALID", ex.code);
        verify(letterMapper, never()).update(any(), any());
    }

    // ── patchDraft: optimistic concurrency (version/ETag) ──────────────────────────

    @Test
    @DisplayName("1.8: a matching expectedVersion succeeds and bumps versionNo")
    void patchDraft_matchingVersion_succeeds() {
        SlowLetter letter = draftLetter(3L, 10L, 2);
        when(letterMapper.selectById(3L)).thenReturn(letter);
        when(letterMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);

        SlowLetter result = service().patchDraft(10L, 3L, "revised title", "revised body", 2);

        assertEquals("revised title", result.title);
        assertEquals("revised body", result.letterBody);
        assertEquals(3, result.versionNo, "versionNo must bump from 2 to 3");

        ArgumentCaptor<UpdateWrapper> captor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(letterMapper).update(any(), captor.capture());
        UpdateWrapper<?> wrapper = captor.getValue();
        // The atomic update must condition on id + owner + DRAFT + the client's expected version
        // all at once -- a real lost-update guard, not a read-then-blind-write.
        assertNotNull(wrapper.getSqlSet(), "must SET the new title/body/version");
        String whereSql = wrapper.getSqlSegment();
        org.junit.jupiter.api.Assertions.assertTrue(whereSql.contains("sender_user_id"));
        org.junit.jupiter.api.Assertions.assertTrue(whereSql.contains("status"));
        org.junit.jupiter.api.Assertions.assertTrue(whereSql.contains("version_no"));
    }

    @Test
    @DisplayName("1.8: a STALE expectedVersion (lost concurrent-edit race) is rejected as CONFLICT, not silently applied")
    void patchDraft_staleVersion_rejectedAsConflict() {
        SlowLetter letter = draftLetter(4L, 10L, 3); // current version is already 3
        when(letterMapper.selectById(4L)).thenReturn(letter);
        // The atomic conditional UPDATE (WHERE version_no = expected) affects 0 rows because the
        // real current version (3) no longer matches this caller's stale belief (expected=1).
        when(letterMapper.update(any(), any(UpdateWrapper.class))).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service().patchDraft(10L, 4L, "stale edit", "stale body", 1));

        assertEquals("CONFLICT", ex.code);
    }

    // ── idempotency: draft() ────────────────────────────────────────────────────────

    private LetterCreateRequest request(String idempotencyKey) {
        LetterCreateRequest r = new LetterCreateRequest();
        r.receiverUserId = 20L;
        r.title = "致远方";
        r.letterBody = "今天想和你说说话。";
        r.idempotencyKey = idempotencyKey;
        return r;
    }

    @Test
    @DisplayName("1.8: draft() with an idempotency key that already resolves to a letter returns the SAME letter, no new insert")
    void draft_idempotencyKeyAlreadyExists_returnsExistingWithoutReinserting() {
        SlowLetter existing = draftLetter(5L, 1L, 0);
        when(letterMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);

        SlowLetter result = service().draft(1L, request("draft-key-1"));

        assertEquals(existing, result);
        verify(letterMapper, never()).insert(any(SlowLetter.class));
        // Safety/validation for a brand-new draft must not even run on a pure replay.
        verify(guardAgent, never()).allow(any());
    }

    @Test
    @DisplayName("1.8: draft() insert racing a concurrent retry of the SAME idempotency key returns the WINNER's row, not an error")
    void draft_concurrentIdempotencyRace_returnsWinner() {
        when(letterMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(null) // first check: no existing row yet
                .thenReturn(draftLetter(6L, 1L, 0)); // after the race: the winner's row
        when(guardAgent.allow(any())).thenReturn(true);
        when(letterMapper.insert(any(SlowLetter.class))).thenThrow(new DuplicateKeyException("dup"));

        SlowLetter result = service().draft(1L, request("draft-key-2"));

        assertEquals(6L, result.id, "must present the winning insert's row, not throw or double-insert");
    }

    // ── idempotency: replyWithLetter() ──────────────────────────────────────────────

    @Test
    @DisplayName("1.8: replyWithLetter() with an idempotency key that already resolves to a reply returns the SAME reply, no new insert/thread bump")
    void replyWithLetter_idempotencyKeyAlreadyExists_returnsExistingWithoutReinserting() {
        SlowLetter existingReply = draftLetter(7L, 20L, 0);
        when(letterMapper.selectOne(any(QueryWrapper.class))).thenReturn(existingReply);

        SlowLetter result = service().replyWithLetter(20L, 1L, request("reply-key-1"));

        assertEquals(existingReply, result);
        verify(letterMapper, never()).selectById(any());
        verify(letterMapper, never()).insert(any(SlowLetter.class));
        verify(threadMapper, never()).insert(any(com.innercosmos.entity.LetterThread.class));
    }
}
