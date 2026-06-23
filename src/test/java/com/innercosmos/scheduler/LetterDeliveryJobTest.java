package com.innercosmos.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * VS-006: the LetterDeliveryJob must retry a failed SENT→DELIVERED advance a
 * bounded number of times instead of silently dropping the letter, and must
 * leave the letter SENT so the next tick retries it. The LetterState machine's
 * valid transitions are unchanged.
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
        // Use a near-zero backoff so the retry test stays fast.
        job = new LetterDeliveryJob(letterMapper, logMapper); // M-077: audit-log mapper
    }

    private SlowLetter sentLetter(long id) {
        SlowLetter letter = new SlowLetter();
        letter.id = id;
        letter.status = "SENT";
        letter.estimatedArrivalAt = LocalDateTime.now().minusMinutes(5);
        return letter;
    }

    /** Returns a fresh SENT snapshot on every selectById call, mirroring how the
     * job re-reads the row from the DB between retry attempts (so a mutation by
     * a failed attempt does not leak into the next attempt's read). */
    private static SlowLetter freshSent(long id) {
        SlowLetter letter = new SlowLetter();
        letter.id = id;
        letter.status = "SENT";
        letter.estimatedArrivalAt = LocalDateTime.now().minusMinutes(5);
        return letter;
    }

    @Test
    @DisplayName("Successful advance delivers on the first attempt")
    void deliver_successOnFirstAttempt() {
        SlowLetter letter = sentLetter(1L);
        when(letterMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(letter));
        when(letterMapper.selectById(1L)).thenReturn(letter);
        when(letterMapper.updateById(any(SlowLetter.class))).thenReturn(1);

        job.deliverArrivedLetters();

        verify(letterMapper, times(2)).updateById(any(SlowLetter.class)); // M-068: SENT→FLYING + FLYING→DELIVERED
        assertEquals("DELIVERED", letter.status);
    }

    @Test
    @DisplayName("Transient failure is retried and succeeds within MAX_ATTEMPTS")
    void deliver_transientFailure_retriedAndSucceeds() {
        SlowLetter letter = sentLetter(2L);
        when(letterMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(letter));
        // Each retry re-reads a fresh SENT row from the DB.
        when(letterMapper.selectById(2L)).thenAnswer(inv -> freshSent(2L));
        // First two updates fail, third succeeds.
        when(letterMapper.updateById(any(SlowLetter.class)))
                .thenThrow(new RuntimeException("db hiccup"))
                .thenThrow(new RuntimeException("db hiccup"))
                .thenReturn(1);

        job.deliverArrivedLetters();

        verify(letterMapper, times(LetterDeliveryJob.MAX_ATTEMPTS)).updateById(any(SlowLetter.class));
    }

    @Test
    @DisplayName("Persistent failure exhausts retries and leaves the letter SENT (no silent drop)")
    void deliver_persistentFailure_leavesSentAndRetriesBoundedTimes() {
        SlowLetter letter = sentLetter(3L);
        when(letterMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(letter));
        when(letterMapper.selectById(3L)).thenAnswer(inv -> freshSent(3L));
        when(letterMapper.updateById(any(SlowLetter.class)))
                .thenThrow(new RuntimeException("persistent db error"));

        job.deliverArrivedLetters();

        // Retried exactly MAX_ATTEMPTS times — no unbounded loop.
        verify(letterMapper, times(LetterDeliveryJob.MAX_ATTEMPTS)).updateById(any(SlowLetter.class));
    }

    @Test
    @DisplayName("Letter already transitioned out of SENT is not force-advanced")
    void deliver_alreadyDelivered_notAdvancedAgain() {
        SlowLetter letter = sentLetter(4L);
        when(letterMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(letter));
        SlowLetter alreadyDelivered = sentLetter(4L);
        alreadyDelivered.status = "DELIVERED";
        when(letterMapper.selectById(4L)).thenReturn(alreadyDelivered);

        job.deliverArrivedLetters();

        // State machine respected: never transitions DELIVERED→DELIVERED via this job.
        verify(letterMapper, never()).updateById(any(SlowLetter.class));
    }
}
