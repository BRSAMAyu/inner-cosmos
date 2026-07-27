package com.innercosmos.conversation.service;

import com.innercosmos.conversation.vo.TurnTimelineVO;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.vo.AuroraReplyVO;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

public interface ConversationChoreographyService {
    record DeliveryLease(String owner, long fencingToken, LocalDateTime expiresAt) {}
    record GenerationRequestSnapshot(
            Long turnId,
            Long sessionId,
            Long userMessageId,
            String mode,
            String locale,
            String region,
            String timezone,
            String contextVersion,
            boolean foregroundAcknowledgementSent) {}

    TurnTimelineVO beginTurn(Long userId, Long sessionId, Long userMessageId);

    TurnTimelineVO commitPlan(Long userId, Long turnId, AuroraReplyVO reply);

    GenerationRequestSnapshot stageGenerationRequest(
            Long userId, Long turnId, Long sessionId, Long userMessageId,
            String mode, String locale, String region, String timezone,
            String contextVersion, boolean foregroundAcknowledgementSent);

    GenerationRequestSnapshot generationRequest(Long userId, Long turnId);

    DeliveryLease claimGenerationLease(Long userId, Long turnId, String owner, Duration ttl);

    TurnTimelineVO commitPlanFenced(Long userId, Long turnId, AuroraReplyVO reply,
                                    String owner, long fencingToken);

    /**
     * Persist the next user-safe planner snapshot before speaker generation. The caller supplies
     * the revision it read; only exactly {@code expectedPriorRevision + 1} may be committed.
     */
    TurnTimelineVO stageDeliberation(Long userId, Long turnId,
                                     int expectedPriorRevision, String userSafeSnapshotJson);

    TurnTimelineVO stageDeliberationFenced(
            Long userId, Long turnId, int expectedPriorRevision, String userSafeSnapshotJson,
            String owner, long fencingToken, Duration ttl);

    TurnTimelineVO commitBubble(Long userId, Long turnId, int bubbleOrder, DialogMessage persistedBubble);

    TurnTimelineVO deliverBubble(Long userId, Long turnId, int bubbleOrder,
                                 Supplier<DialogMessage> messagePersistence);

    void recordBubbleProgress(Long userId, Long turnId, int bubbleOrder, int deliveredChars);

    DeliveryLease claimDeliveryLease(Long userId, Long turnId, String owner, Duration ttl);

    boolean renewDeliveryLease(Long userId, Long turnId, String owner, long fencingToken, Duration ttl);

    void recordBubbleProgressFenced(Long userId, Long turnId, int bubbleOrder, int deliveredChars,
                                    String owner, long fencingToken, Duration ttl);

    TurnTimelineVO deliverBubbleFenced(Long userId, Long turnId, int bubbleOrder,
                                       String owner, long fencingToken, Duration ttl,
                                       Supplier<DialogMessage> messagePersistence);

    TurnTimelineVO completeTurnFenced(Long userId, Long turnId, String owner, long fencingToken);

    TurnTimelineVO failUnrecoverableGeneration(Long userId, Long turnId,
                                               LocalDateTime cutoff, String reason);

    TurnTimelineVO completeTurn(Long userId, Long turnId);

    TurnTimelineVO cancelTurn(Long userId, Long turnId, String reason);

    TurnTimelineVO interruptIfStale(Long userId, Long turnId, LocalDateTime cutoff, String reason);

    void cancelActiveTurns(Long userId, Long sessionId, String reason);

    boolean isCancelled(Long userId, Long turnId);

    String latestInterruptionContext(Long userId, Long sessionId);
    String latestInterruptionContext(Long userId, Long sessionId, String newUserMessage);

    TurnTimelineVO recordCompletedTurn(Long userId, Long sessionId, Long userMessageId,
                                       AuroraReplyVO reply, List<DialogMessage> persistedBubbles);

    TurnTimelineVO timeline(Long userId, Long turnId);
}
