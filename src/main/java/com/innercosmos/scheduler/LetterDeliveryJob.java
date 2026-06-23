package com.innercosmos.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.LetterStatusLog;
import com.innercosmos.entity.SlowLetter;
import com.innercosmos.mapper.LetterStatusLogMapper;
import com.innercosmos.mapper.SlowLetterMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    public void deliverArrivedLetters() {
        QueryWrapper<SlowLetter> query = new QueryWrapper<>();
        query.eq("status", "SENT")
                .le("estimated_arrival_at", LocalDateTime.now());
        List<SlowLetter> letters = letterMapper.selectList(query);
        int delivered = 0;
        int failed = 0;
        for (SlowLetter letter : letters) {
            if (advanceWithRetry(letter)) {
                delivered++;
            } else {
                failed++;
            }
        }
        if (delivered > 0) {
            log.info("Delivered {} of {} letters", delivered, letters.size());
        }
        if (failed > 0) {
            log.warn("Failed to deliver {} of {} letters after {} attempts; will retry on next tick",
                    failed, letters.size(), MAX_ATTEMPTS);
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
    private boolean advanceWithRetry(SlowLetter letter) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                SlowLetter fresh = letterMapper.selectById(letter.id);
                // Only advance a letter that is still SENT — never violate the
                // state machine by transitioning from another state.
                if (fresh == null || !"SENT".equals(fresh.status)) {
                    log.info("Letter {} no longer SENT (state={}); skipping delivery",
                            letter.id, fresh == null ? "DELETED" : fresh.status);
                    return true;
                }
                fresh.status = "DELIVERED";
                fresh.deliveredAt = LocalDateTime.now();
                letterMapper.updateById(fresh);
                // M-077: write the audit-log entry for scheduler-driven delivery so the letter
                // lifecycle trail is complete (was API-transition-only before).
                try {
                    LetterStatusLog entry = new LetterStatusLog();
                    entry.letterId = fresh.id;
                    entry.fromStatus = "SENT";
                    entry.toStatus = "DELIVERED";
                    entry.operatorUserId = null; // system / scheduler
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
