package com.innercosmos.service;

import com.innercosmos.entity.WakeIntent;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public interface WakeIntentService {
    String POLICY_VERSION = "wake-intent.v1";

    WakeIntent schedule(Long userId, String purpose, String reasonForUser, String content,
                        LocalDateTime earliestAt, LocalDateTime preferredAt, LocalDateTime latestAt,
                        String timezone, String payloadRef);
    WakeIntent scheduleAtInstants(Long userId, String purpose, String reasonForUser, String content,
                                  Instant earliestAt, Instant preferredAt, Instant latestAt,
                                  String timezone, String payloadRef);
    WakeIntent scheduleNatural(Long userId, String expression, String purpose, String reasonForUser,
                               String content, String timezone, Long contextSessionId);
    WakeIntent getOwned(Long userId, Long intentId);
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
    WakeIntent feedback(Long userId, Long intentId, String choice);
    boolean isPurposeMuted(Long userId, String purpose);
    int expirePastDue();
}
