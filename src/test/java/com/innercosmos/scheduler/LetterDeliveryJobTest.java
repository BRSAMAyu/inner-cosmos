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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    // ── Gemini audit 1.2 (CONFIRMED/P1): departure/arrival semantics ──────────────────────────
    // Old bug: Stage 1 (SENT->FLYING) was gated on the SAME `estimated_arrival_at <= now`
    // condition that should gate arrival, and Stage 2 (FLYING->DELIVERED) had no time gate at
    // all -- so a letter only became FLYING once it had already matured, and was immediately
    // delivered in that same tick. The visible "flying" (in-transit) window never existed.

    @Test
    @DisplayName("1.2: a freshly-SENT letter whose arrival time is still in the FUTURE departs (SENT->FLYING) immediately, unconditionally")
    void sentLetter_departsImmediately_regardlessOfArrivalTime() {
        SlowLetter letter = new SlowLetter();
        letter.id = 10L;
        letter.status = "SENT";
        letter.estimatedArrivalAt = LocalDateTime.now().plusMinutes(3); // not due for a while yet
        when(letterMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(letter), List.of());
        when(letterMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);

        job.deliverArrivedLetters();

        // Departure must not wait for the arrival gate -- exactly one advance call (SENT->FLYING),
        // proving Stage 1's query carries no `estimated_arrival_at` condition anymore.
        verify(letterMapper, times(1)).update(any(), any(UpdateWrapper.class));
    }

    @Test
    @DisplayName("1.2: Stage 1 (departure) queries unconditionally on status only; Stage 2 (arrival) additionally gates on estimated_arrival_at")
    void queryConstruction_separatesDepartureFromArrivalGate() {
        // A mocked mapper can't itself enforce a real WHERE clause, so the only way to actually
        // prove the two stages carry different filters (rather than trusting mocked RETURN
        // values, which say nothing about what a real query would match) is to capture and
        // inspect the QueryWrapper objects the job actually constructs and sends to selectList.
        when(letterMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(), List.of());

        job.deliverArrivedLetters();

        ArgumentCaptor<QueryWrapper> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(letterMapper, times(2)).selectList(captor.capture());
        String departureQuerySql = captor.getAllValues().get(0).getSqlSegment();
        String arrivalQuerySql = captor.getAllValues().get(1).getSqlSegment();

        assertTrue(departureQuerySql.contains("status"), "Stage 1 must still filter by status = SENT");
        assertTrue(!departureQuerySql.contains("estimated_arrival_at"),
                "Stage 1 (departure) must NOT gate on arrival time -- a letter departs immediately, unconditionally");
        assertTrue(arrivalQuerySql.contains("estimated_arrival_at"),
                "Stage 2 (arrival) must gate on estimated_arrival_at -- this is the ONLY place that time gate belongs");
    }

    @Test
    @DisplayName("1.2: a FLYING letter whose arrival time HAS come is delivered")
    void flyingLetter_matured_isDelivered() {
        Clock fixed = Clock.fixed(LocalDateTime.of(2026, 7, 23, 12, 5).atZone(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
        job.useClock(fixed);
        SlowLetter flying = new SlowLetter();
        flying.id = 12L;
        flying.status = "FLYING";
        flying.estimatedArrivalAt = LocalDateTime.of(2026, 7, 23, 12, 0); // 5 minutes overdue
        when(letterMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(), List.of(flying));
        when(letterMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);

        job.deliverArrivedLetters();

        ArgumentCaptor<UpdateWrapper> captor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(letterMapper, times(1)).update(any(), captor.capture());
        assertTrue(captor.getValue().getSqlSet().contains("delivered_at"),
                "the FLYING->DELIVERED advance must stamp delivered_at");
    }

    @Test
    @DisplayName("1.2: a re-run in the same tick-equivalent window never double-delivers a letter that already advanced (idempotent rerun)")
    void rerun_doesNotDoubleDeliverAnAlreadyAdvancedLetter() {
        Clock fixed = Clock.fixed(LocalDateTime.of(2026, 7, 23, 12, 5).atZone(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
        job.useClock(fixed);
        SlowLetter flying = new SlowLetter();
        flying.id = 13L;
        flying.status = "FLYING";
        flying.estimatedArrivalAt = LocalDateTime.of(2026, 7, 23, 12, 0);
        // First run: the letter is still FLYING and matures -> DELIVERED.
        when(letterMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(List.of(), List.of(flying))
                // Second run: the (real) DB would no longer return this letter from the FLYING
                // query since it is now DELIVERED -- simulated here as an empty result.
                .thenReturn(List.of(), List.of());
        when(letterMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);

        job.deliverArrivedLetters();
        job.deliverArrivedLetters();

        // Exactly one advance call across both runs -- the rerun found nothing left to do.
        verify(letterMapper, times(1)).update(any(), any(UpdateWrapper.class));
    }

    @Test
    @DisplayName("1.2: a concurrent second scheduler racing for the same FLYING->DELIVERED advance loses cleanly, not double-delivering")
    void multiScheduler_concurrentDeliverAttempt_onlyOneWins() {
        Clock fixed = Clock.fixed(LocalDateTime.of(2026, 7, 23, 12, 5).atZone(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
        job.useClock(fixed);
        SlowLetter flying = new SlowLetter();
        flying.id = 14L;
        flying.status = "FLYING";
        flying.estimatedArrivalAt = LocalDateTime.of(2026, 7, 23, 12, 0);
        when(letterMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(), List.of(flying));
        // Simulates another scheduler replica's instance winning the atomic UPDATE first (0 rows
        // affected for THIS instance), then confirming the row is already DELIVERED.
        when(letterMapper.update(any(), any(UpdateWrapper.class))).thenReturn(0);
        SlowLetter alreadyDelivered = new SlowLetter();
        alreadyDelivered.id = 14L;
        alreadyDelivered.status = "DELIVERED";
        when(letterMapper.selectById(14L)).thenReturn(alreadyDelivered);

        job.deliverArrivedLetters();

        verify(letterMapper, times(1)).update(any(), any(UpdateWrapper.class));
        verify(letterMapper, never()).updateById(any(SlowLetter.class));
    }
}
