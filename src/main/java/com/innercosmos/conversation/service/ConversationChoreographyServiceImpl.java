package com.innercosmos.conversation.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.conversation.entity.ConversationEvent;
import com.innercosmos.conversation.entity.ConversationTurn;
import com.innercosmos.conversation.entity.GenerationAttempt;
import com.innercosmos.conversation.entity.MessageBubble;
import com.innercosmos.conversation.entity.TurnPlan;
import com.innercosmos.conversation.vo.TurnTimelineVO;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.ConversationEventMapper;
import com.innercosmos.mapper.ConversationTurnMapper;
import com.innercosmos.mapper.GenerationAttemptMapper;
import com.innercosmos.mapper.MessageBubbleMapper;
import com.innercosmos.mapper.TurnPlanMapper;
import com.innercosmos.vo.AuroraReplyVO;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationChoreographyServiceImpl implements ConversationChoreographyService {
    private final ConversationTurnMapper turnMapper;
    private final TurnPlanMapper planMapper;
    private final MessageBubbleMapper bubbleMapper;
    private final ConversationEventMapper eventMapper;
    private final GenerationAttemptMapper attemptMapper;
    private final ObjectMapper objectMapper;

    public ConversationChoreographyServiceImpl(ConversationTurnMapper turnMapper,
                                                TurnPlanMapper planMapper,
                                                MessageBubbleMapper bubbleMapper,
                                                ConversationEventMapper eventMapper,
                                                GenerationAttemptMapper attemptMapper,
                                                ObjectMapper objectMapper) {
        this.turnMapper = turnMapper;
        this.planMapper = planMapper;
        this.bubbleMapper = bubbleMapper;
        this.eventMapper = eventMapper;
        this.attemptMapper = attemptMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * Adapts the already accepted Aurora output into the durable choreography model.
     * The unique user_message_id and turn/plan constraints are the cross-replica
     * idempotency authority: a retry can observe the existing turn but cannot commit a
     * second plan or a second copy of its bubbles.
     */
    @Override
    @Transactional
    public TurnTimelineVO recordCompletedTurn(Long userId, Long sessionId, Long userMessageId,
                                              AuroraReplyVO reply, List<DialogMessage> persistedBubbles) {
        if (userId == null || sessionId == null || userMessageId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "对话编排缺少 turn identity");
        }
        ConversationTurn existing = turnMapper.selectOne(new QueryWrapper<ConversationTurn>()
                .eq("user_id", userId).eq("user_message_id", userMessageId).last("LIMIT 1"));
        if (existing != null) {
            return timeline(userId, existing.id);
        }

        LocalDateTime now = LocalDateTime.now();
        ConversationTurn turn = new ConversationTurn();
        turn.sessionId = sessionId;
        turn.userId = userId;
        turn.userMessageId = userMessageId;
        turn.status = "PLANNING";
        turn.nextEventSequence = 1;
        turn.startedAt = now;
        turnMapper.insert(turn);
        appendEvent(turn, null, null, "TURN_CREATED", null,
                Map.of("sessionId", sessionId, "userMessageId", userMessageId));

        TurnPlan plan = new TurnPlan();
        plan.turnId = turn.id;
        plan.userId = userId;
        plan.planVersion = 1;
        plan.commitSlot = 1;
        plan.status = "COMMITTED";
        plan.intent = blankTo(reply == null ? null : reply.detectedTheme, "陪伴与回应");
        plan.posture = blankTo(reply == null ? null : reply.replyTone, "温柔、具体、像朋友");
        plan.stopCondition = "ALL_BUBBLES_COMMITTED";
        plan.committedAt = now;
        planMapper.insert(plan);
        appendEvent(turn, plan.id, null, "PLAN_COMMITTED", "turn:" + turn.id,
                Map.of("planVersion", 1, "bubbleCount", persistedBubbles == null ? 0 : persistedBubbles.size()));

        GenerationAttempt attempt = new GenerationAttempt();
        attempt.turnId = turn.id;
        attempt.planId = plan.id;
        attempt.userId = userId;
        attempt.attemptNumber = 1;
        attempt.status = "COMPLETED";
        attempt.provider = aiState(reply, "provider");
        attempt.modelName = aiState(reply, "model");
        attempt.startedAt = now;
        attempt.completedAt = now;
        attemptMapper.insert(attempt);

        List<DialogMessage> safeBubbles = persistedBubbles == null ? List.of() : persistedBubbles;
        for (int i = 0; i < safeBubbles.size(); i++) {
            DialogMessage persisted = safeBubbles.get(i);
            MessageBubble bubble = new MessageBubble();
            bubble.turnId = turn.id;
            bubble.planId = plan.id;
            bubble.userId = userId;
            bubble.dialogMessageId = persisted == null ? null : persisted.id;
            bubble.bubbleOrder = i + 1;
            bubble.purpose = purpose(i, safeBubbles.size());
            bubble.content = persisted == null ? "" : persisted.textContent;
            bubble.status = "COMMITTED";
            bubble.sendAfterMs = i == 0 ? 0 : 220;
            bubble.requiresNoInterruption = i > 0;
            bubble.plannedAt = now;
            bubble.sentAt = now;
            bubbleMapper.insert(bubble);
            appendEvent(turn, plan.id, bubble.id, "BUBBLE_PLANNED", "plan:" + plan.id,
                    Map.of("order", i + 1, "purpose", bubble.purpose));
            appendEvent(turn, plan.id, bubble.id, "BUBBLE_COMMITTED", "bubble:" + bubble.id,
                    Map.of("dialogMessageId", bubble.dialogMessageId == null ? -1L : bubble.dialogMessageId));
        }

        turn.activePlanId = plan.id;
        turn.status = "COMPLETED";
        turn.completedAt = now;
        turnMapper.updateById(turn);
        appendEvent(turn, plan.id, null, "TURN_COMPLETED", "plan:" + plan.id,
                Map.of("committedBubbleCount", safeBubbles.size()));
        turnMapper.updateById(turn); // persist the final nextEventSequence
        return timeline(userId, turn.id);
    }

    @Override
    @Transactional(readOnly = true)
    public TurnTimelineVO timeline(Long userId, Long turnId) {
        ConversationTurn turn = turnMapper.selectOne(new QueryWrapper<ConversationTurn>()
                .eq("id", turnId).eq("user_id", userId).last("LIMIT 1"));
        if (turn == null) {
            // Opaque not-found prevents turn-id enumeration from becoming an IDOR oracle.
            throw new BusinessException(ErrorCode.NOT_FOUND, "对话时间线不存在或不可访问");
        }
        TurnTimelineVO vo = new TurnTimelineVO();
        vo.turn = turn;
        if (turn.activePlanId != null) {
            vo.activePlan = planMapper.selectOne(new QueryWrapper<TurnPlan>()
                    .eq("id", turn.activePlanId).eq("user_id", userId).last("LIMIT 1"));
        }
        vo.bubbles = bubbleMapper.selectList(new QueryWrapper<MessageBubble>()
                .eq("turn_id", turn.id).eq("user_id", userId).orderByAsc("bubble_order"));
        vo.events = eventMapper.selectList(new QueryWrapper<ConversationEvent>()
                .eq("turn_id", turn.id).eq("user_id", userId).orderByAsc("event_sequence"));
        vo.generationAttempts = attemptMapper.selectList(new QueryWrapper<GenerationAttempt>()
                .eq("turn_id", turn.id).eq("user_id", userId).orderByAsc("attempt_number"));
        return vo;
    }

    private void appendEvent(ConversationTurn turn, Long planId, Long bubbleId,
                             String type, String causationId, Map<String, ?> payload) {
        ConversationEvent event = new ConversationEvent();
        event.turnId = turn.id;
        event.planId = planId;
        event.bubbleId = bubbleId;
        event.userId = turn.userId;
        event.eventSequence = turn.nextEventSequence++;
        event.eventType = type;
        event.causationId = causationId;
        event.payloadJson = json(payload);
        eventMapper.insert(event);
    }

    private String json(Map<String, ?> payload) {
        try {
            return objectMapper.writeValueAsString(new LinkedHashMap<>(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("conversation event payload serialization failed", e);
        }
    }

    private String aiState(AuroraReplyVO reply, String key) {
        Object value = reply == null || reply.aiState == null ? null : reply.aiState.get(key);
        return value == null ? null : value.toString();
    }

    private String purpose(int index, int total) {
        if (index == 0) return "ACKNOWLEDGE";
        if (index == total - 1) return "GENTLE_NEXT_STEP";
        return "DEEPEN";
    }

    private String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
