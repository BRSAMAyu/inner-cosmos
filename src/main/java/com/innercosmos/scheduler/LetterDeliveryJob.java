package com.innercosmos.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.entity.LetterStatusLog;
import com.innercosmos.entity.SlowLetter;
import com.innercosmos.mapper.LetterStatusLogMapper;
import com.innercosmos.mapper.SlowLetterMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Component
@ConditionalOnExpression("'${inner-cosmos.runtime.role:all}' == 'all' or '${inner-cosmos.runtime.role:all}' == 'scheduler'")
public class LetterDeliveryJob {
    private static final Logger log = LoggerFactory.getLogger(LetterDeliveryJob.class);

    /** Maximum number of SENT→DELIVERED attempts within a single job tick. */
    static final int MAX_ATTEMPTS = 3;
    /** Fixed backoff (ms) between retry attempts inside one tick. */
    static final long RETRY_BACKOFF_MS = 200L;

    private final SlowLetterMapper letterMapper;
    private final LetterStatusLogMapper logMapper;
    // Gemini audit 1.2 (CONFIRMED/P1): package-private setter for tests, matching this
    // codebase's existing Clock-injectable time-policy convention (e.g. GravityTimePolicy).
    private Clock clock = Clock.systemDefaultZone();

    public LetterDeliveryJob(SlowLetterMapper letterMapper, LetterStatusLogMapper logMapper) {
        this.letterMapper = letterMapper;
        this.logMapper = logMapper;
    }

    void useClock(Clock fixedClock) {
        this.clock = fixedClock;
    }

    @Scheduled(fixedRate = 60000)
    @SchedulerLock(name = "letter-delivery", lockAtMostFor = "PT5M", lockAtLeastFor = "PT55S")
    public void deliverArrivedLetters() {
        LocalDateTime now = LocalDateTime.now(clock);
        // Gemini audit 1.2 (CONFIRMED/P1): the old Stage 1 query gated SENT->FLYING on
        // `estimated_arrival_at <= now` -- the SAME condition that should gate arrival -- so a
        // letter only ever became visibly FLYING once it had ALREADY matured, and Stage 2 (below)
        // had no time gate at all, immediately delivering it in that same tick. The visible
        // "flying" period the parallax delay is meant to produce never actually existed: a letter
        // went from SENT straight to DELIVERED with no observable in-transit window.
        //
        // Departure/arrival semantics, now separated: a letter departs (SENT->FLYING) as soon as
        // it exists, unconditionally -- FLYING covers the whole visible parallax window, not just
        // its final instant. It arrives (FLYING->DELIVERED) only once `estimated_arrival_at` has
        // actually passed, which is the ONLY place that time gate belongs.
        QueryWrapper<SlowLetter> sentQuery = new QueryWrapper<>();
        sentQuery.eq("status", "SENT");
        List<SlowLetter> sentLetters = letterMapper.selectList(sentQuery);
        int flown = 0;
        for (SlowLetter letter : sentLetters) {
            if (advanceWithRetry(letter, "SENT", "FLYING", false)) flown++;
        }
        // Stage 2: FLYING letters whose arrival time has come -> DELIVERED.
        QueryWrapper<SlowLetter> flyingQuery = new QueryWrapper<>();
        flyingQuery.eq("status", "FLYING").le("estimated_arrival_at", now);
        List<SlowLetter> flyingLetters = letterMapper.selectList(flyingQuery);
        int delivered = 0;
        for (SlowLetter letter : flyingLetters) {
            if (advanceWithRetry(letter, "FLYING", "DELIVERED", true)) delivered++;
        }
        if (flown > 0 || delivered > 0) {
            log.info("Letters: {} flown (SENT->FLYING), {} delivered (FLYING->DELIVERED)", flown, delivered);
        }
    }

    /**
     * Advance a single SENT letter to DELIVERED with a bounded number of retry
     * attempts and a short backoff within this tick (VS-006). The letter is left
     * in SENT if every attempt fails, so the next scheduled tick re-queries and
     * retries it — no letter is silently dropped. The valid LetterState
     * transitions are unchanged.
     *
     * Regression (Gemini audit 1.1/2.2, P0): this previously read the row, checked
     * its status in Java, then wrote the whole entity back via updateById() — a
     * read-then-write race. A user action landing between the read and the write
     * (block, decline, withdraw) would be silently overwritten by this scheduler
     * forcing the row back to FLYING/DELIVERED. The update is now a single atomic
     * `UPDATE ... WHERE id=? AND status=?`; 0 rows affected means someone else
     * already changed this letter's status in that exact window, and the
     * scheduler must yield to that newer state rather than retry-overwriting it.
     *
     * @return true if the letter reached DELIVERED (or something else already advanced it
     *         out of fromStatus, needing no further action from this job), false if a
     *         genuine transient failure exhausted every retry attempt
     */
    private boolean advanceWithRetry(SlowLetter letter, String fromStatus, String toStatus, boolean setDeliveredAt) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                UpdateWrapper<SlowLetter> claim = new UpdateWrapper<SlowLetter>()
                        .eq("id", letter.id).eq("status", fromStatus)
                        .set("status", toStatus);
                if (setDeliveredAt) claim.set("delivered_at", LocalDateTime.now());
                int updated = letterMapper.update(null, claim);
                if (updated == 0) {
                    // Never retry a lost race: the letter is no longer at fromStatus, so a
                    // concurrent user action (or, if ever run without SchedulerLock, another
                    // scheduler replica) already decided its fate in this exact window.
                    // Overwriting that would be exactly the bug this fix closes.
                    SlowLetter current = letterMapper.selectById(letter.id);
                    log.info("Letter {} no longer {} (now {}); yielding to the newer state instead of overwriting it",
                            letter.id, fromStatus, current == null ? "deleted" : current.status);
                    return true;
                }
                try {
                    LetterStatusLog entry = new LetterStatusLog();
                    entry.letterId = letter.id;
                    entry.fromStatus = fromStatus;
                    entry.toStatus = toStatus;
                    entry.operatorUserId = null;
                    entry.reason = "scheduled delivery";
                    logMapper.insert(entry);
                } catch (Exception logEx) {
                    log.warn("Failed to record delivery audit log for letter {}: {}", letter.id, logEx.getMessage());
                }
                return true;
            } catch (Exception e) {
                log.warn("Delivery attempt {}/{} failed for letter {}: {}",
                        attempt, MAX_ATTEMPTS, letter.id, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    sleepQuietly(RETRY_BACKOFF_MS);
                }
            }
        }
        // All attempts exhausted — leave SENT for the next tick. Recorded so the
        // failure is observable instead of silently dropped.
        log.error("Letter {} delivery failed after {} attempts; leaving SENT for next tick",
                letter.id, MAX_ATTEMPTS);
        return false;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
