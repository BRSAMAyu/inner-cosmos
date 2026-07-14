package com.innercosmos.conversation.vo;

import com.innercosmos.conversation.entity.ConversationEvent;
import com.innercosmos.conversation.entity.ConversationTurn;
import com.innercosmos.conversation.entity.GenerationAttempt;
import com.innercosmos.conversation.entity.MessageBubble;
import com.innercosmos.conversation.entity.TurnPlan;
import java.util.List;

/** Owner-scoped, replayable view of one Aurora conversation turn. */
public class TurnTimelineVO {
    public ConversationTurn turn;
    public TurnPlan activePlan;
    public List<MessageBubble> bubbles = List.of();
    public List<ConversationEvent> events = List.of();
    public List<GenerationAttempt> generationAttempts = List.of();
}
