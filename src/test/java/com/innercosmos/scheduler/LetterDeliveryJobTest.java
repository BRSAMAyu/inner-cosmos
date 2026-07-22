package com.innercosmos.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.entity.SlowLetter;
import com.innercosmos.mapper.LetterStatusLogMapper;
import com.innercosmos.mapper.SlowLetterMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * VS-006: the LetterDeliveryJob must retry a failed SENT→DELIVERED advance a
 * bounded number of times instead of silently dropping the letter, and must
 * leave the letter SENT so the next tick retries it. The LetterState machine's
 * valid transitions are unchanged.
 *
 * Regression (Gemini audit 1.1/2.2, P0): the advance is now an atomic
 * `UPDATE ... WHERE id=? AND status=?` (letterMapper.update(null, wrapper)),
 * not a read-then-updateById() race. deliver_userActionWins_schedulerYields
 * pins the actual bug: a user action (block/decline/withdraw) landing between
 * this job's list query and its per-letter update must never be overwritten.
 */
@ExtendWith(MockitoExtension.class)
class LetterDeliveryJobTest {

    @Mock
    private SlowLetterMapper letterMapper;

    @Mock
    private LetterStatusLogMapper logMapper;

    private LetterDeliveryJob job;

    @BeforeEach
    void setUp() {
        job = new LetterDeliveryJob(letterMapper, logMapper); // M-077: audit-log mapper
    }

    private SlowLetter sentLetter(long id) {
        SlowLetter letter = new SlowLetter();
        letter.id = id;
        letter.status = "SENT";
        letter.estimatedArrivalAt = LocalDateTime.now().minusMinutes(5);
        return letter;
    }

    @Test
    @DisplayName("Successful advance delivers on the first attempt via an atomic conditional update")
    void deliver_successOnFirstAttempt() {
        SlowLetter letter = sentLetter(1L);
        when(letterMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(letter), List.of());
        when(letterMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);

        job.deliverArrivedLetters();

        verify(letterMapper, times(1)).update(any(), any(UpdateWrapper.class));
        verify(letterMapper, never()).updateById(any(SlowLetter.class));
    }

    @Test
    @DisplayName("Transient failure is retried and succeeds within MAX_ATTEMPTS")
    void deliver_transientFailure_retriedAndSucceeds() {
        SlowLetter letter = sentLetter(2L);
        when(letterMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(letter), List.of());
        // First two conditional-update attempts throw (transient DB error), third succeeds.
        when(letterMapper.update(any(), any(UpdateWrapper.class)))
                .thenThrow(new RuntimeException("db hiccup"))
                .thenThrow(new RuntimeException("db hiccup"))
                .thenReturn(1);

        job.deliverArrivedLetters();

        verify(letterMapper, times(LetterDeliveryJob.MAX_ATTEMPTS)).update(any(), any(UpdateWrapper.class));
    }

    @Test
    @DisplayName("Persistent failure exhausts retries and leaves the letter SENT (no silent drop)")
    void deliver_persistentFailure_leavesSentAndRetriesBoundedTimes() {
        SlowLetter letter = sentLetter(3L);
        when(letterMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(letter), List.of());
        when(letterMapper.update(any(), any(UpdateWrapper.class)))
                .thenThrow(new RuntimeException("persistent db error"));

        job.deliverArrivedLetters();

        // Retried exactly MAX_ATTEMPTS times — no unbounded loop.
        verify(letterMapper, times(LetterDeliveryJob.MAX_ATTEMPTS)).update(any(), any(UpdateWrapper.class));
    }

    @Test
    @DisplayName("A concurrent user action (block/decline/withdraw) between list-fetch and update wins -- the scheduler never overwrites it")
    void deliver_userActionWins_schedulerYields() {
        // The list query snapshot still shows SENT/FLYING (taken at tick start); by the time the
        // atomic conditional update runs, a user action has already moved the real row to BLOCKED.
        // The conditional UPDATE ... WHERE status='SENT' therefore matches 0 rows.
        SlowLetter letter = sentLetter(4L);
        when(letterMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(letter), List.of());
        when(letterMapper.update(any(), any(UpdateWrapper.class))).thenReturn(0);
        SlowLetter nowBlocked = sentLetter(4L);
        nowBlocked.status = "BLOCKED";
        when(letterMapper.selectById(4L)).thenReturn(nowBlocked);

        job.deliverArrivedLetters();

        // Exactly one conditional-update attempt: a 0-row result is a lost race, not a transient
        // failure, so it must NOT be retried (retrying would just find 0 rows again).
        verify(letterMapper, times(1)).update(any(), any(UpdateWrapper.class));
        // The scheduler must never fall back to force-writing the row via updateById().
        verify(letterMapper, never()).updateById(any(SlowLetter.class));
        // And the in-memory snapshot the scheduler read at tick start must not be mutated back
        // into looking delivered -- the real authoritative state is BLOCKED, not FLYING/DELIVERED.
        assertEquals("SENT", letter.status);
    }

    @Test
    @DisplayName("Letter already transitioned out of SENT before the update attempt is not force-advanced")
    void deliver_alreadyDelivered_notAdvancedAgain() {
        SlowLetter letter = sentLetter(5L);
        when(letterMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(letter), List.of());
        when(letterMapper.update(any(), any(UpdateWrapper.class))).thenReturn(0);
        SlowLetter alreadyDelivered = sentLetter(5L);
        alreadyDelivered.status = "DELIVERED";
        when(letterMapper.selectById(5L)).thenReturn(alreadyDelivered);

        job.deliverArrivedLetters();

        // State machine respected: never force-writes DELIVERED again via updateById.
        verify(letterMapper, never()).updateById(any(SlowLetter.class));
    }
}
