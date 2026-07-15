package com.innercosmos.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.LetterStatusLog;
import com.innercosmos.entity.SlowLetter;
import com.innercosmos.mapper.LetterStatusLogMapper;
import com.innercosmos.mapper.SlowLetterMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class LetterDeliveryJob {
    private static final Logger log = LoggerFactory.getLogger(LetterDeliveryJob.class);

    /** Maximum number of SENT→DELIVERED attempts within a single job tick. */
    static final int MAX_ATTEMPTS = 3;
    /** Fixed backoff (ms) between retry attempts inside one tick. */
    static final long RETRY_BACKOFF_MS = 200L;

    private final SlowLetterMapper letterMapper;
    private final LetterStatusLogMapper logMapper;

    public LetterDeliveryJob(SlowLetterMapper letterMapper, LetterStatusLogMapper logMapper) {
        this.letterMapper = letterMapper;
        this.logMapper = logMapper;
    }

    @Scheduled(fixedRate = 60000)
    @SchedulerLock(name = "letter-delivery", lockAtMostFor = "PT5M", lockAtLeastFor = "PT55S")
    public void deliverArrivedLetters() {
        java.time.LocalDateTime now = LocalDateTime.now();
        // M-068: two-stage delivery with a visible FLYING (in-transit) state.
        // Stage 1: SENT letters whose arrival time has come -> FLYING.
        QueryWrapper<SlowLetter> sentQuery = new QueryWrapper<>();
        sentQuery.eq("status", "SENT").le("estimated_arrival_at", now);
        List<SlowLetter> sentLetters = letterMapper.selectList(sentQuery);
        int flown = 0;
        for (SlowLetter letter : sentLetters) {
            if (advanceWithRetry(letter, "SENT", "FLYING", false)) flown++;
        }
        // Stage 2: FLYING letters -> DELIVERED (completes the journey).
        QueryWrapper<SlowLetter> flyingQuery = new QueryWrapper<>();
        flyingQuery.eq("status", "FLYING");
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
     * @return true if the letter reached DELIVERED, false if all attempts failed
     */
    private boolean advanceWithRetry(SlowLetter letter, String fromStatus, String toStatus, boolean setDeliveredAt) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                SlowLetter fresh = letterMapper.selectById(letter.id);
                if (fresh == null || !fromStatus.equals(fresh.status)) {
                    return true; // already advanced or deleted
                }
                fresh.status = toStatus;
                if (setDeliveredAt) fresh.deliveredAt = LocalDateTime.now();
                letterMapper.updateById(fresh);
                try {
                    LetterStatusLog entry = new LetterStatusLog();
                    entry.letterId = fresh.id;
                    entry.fromStatus = fromStatus;
                    entry.toStatus = toStatus;
                    entry.operatorUserId = null;
                    entry.reason = "scheduled delivery";
                    logMapper.insert(entry);
                } catch (Exception logEx) {
                    log.warn("Failed to record delivery audit log for letter {}: {}", fresh.id, logEx.getMessage());
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
