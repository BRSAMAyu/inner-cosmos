package com.innercosmos.service;

import com.innercosmos.entity.WakeIntent;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public interface WakeIntentService {
    String POLICY_VERSION = "wake-intent.v1";

    WakeIntent schedule(Long userId, String purpose, String reasonForUser, String content,
                        LocalDateTime earliestAt, LocalDateTime preferredAt, LocalDateTime latestAt,
                        String timezone, String payloadRef);
    List<WakeIntent> listActive(Long userId);
    WakeIntent cancel(Long userId, Long intentId);
    WakeIntent reschedule(Long userId, Long intentId, LocalDateTime earliestAt,
                          LocalDateTime preferredAt, LocalDateTime latestAt);
    List<WakeIntent> claimDue(String workerId, int batchSize, Duration lease);
    boolean delay(WakeIntent claimed, LocalDateTime nextPreferredAt, String reason);
    boolean finish(WakeIntent claimed, String outcome, String reason);
    /** Atomically completes a claimed intent and persists its durable in-app delivery. */
    boolean finishWithNotification(WakeIntent claimed, String outcome, String reason,
                                   String title, String content);
    int expirePastDue();
}
