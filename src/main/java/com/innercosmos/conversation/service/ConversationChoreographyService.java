package com.innercosmos.conversation.service;

import com.innercosmos.conversation.vo.TurnTimelineVO;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.vo.AuroraReplyVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

public interface ConversationChoreographyService {
    TurnTimelineVO beginTurn(Long userId, Long sessionId, Long userMessageId);

    TurnTimelineVO commitPlan(Long userId, Long turnId, AuroraReplyVO reply);

    TurnTimelineVO commitBubble(Long userId, Long turnId, int bubbleOrder, DialogMessage persistedBubble);

    TurnTimelineVO deliverBubble(Long userId, Long turnId, int bubbleOrder,
                                 Supplier<DialogMessage> messagePersistence);

    void recordBubbleProgress(Long userId, Long turnId, int bubbleOrder, int deliveredChars);

    TurnTimelineVO completeTurn(Long userId, Long turnId);

    TurnTimelineVO cancelTurn(Long userId, Long turnId, String reason);

    TurnTimelineVO interruptIfStale(Long userId, Long turnId, LocalDateTime cutoff, String reason);

    void cancelActiveTurns(Long userId, Long sessionId, String reason);

    boolean isCancelled(Long userId, Long turnId);

    String latestInterruptionContext(Long userId, Long sessionId);

    TurnTimelineVO recordCompletedTurn(Long userId, Long sessionId, Long userMessageId,
                                       AuroraReplyVO reply, List<DialogMessage> persistedBubbles);

    TurnTimelineVO timeline(Long userId, Long turnId);
}
