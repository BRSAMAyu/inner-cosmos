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
import java.util.function.Supplier;
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

    @Override
    @Transactional
    public TurnTimelineVO beginTurn(Long userId, Long sessionId, Long userMessageId) {
        if (userId == null || sessionId == null || userMessageId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "对话编排缺少 turn identity");
        }
        ConversationTurn existing = turnMapper.selectOne(new QueryWrapper<ConversationTurn>()
                .eq("user_id", userId).eq("user_message_id", userMessageId).last("LIMIT 1"));
        if (existing != null) return timeline(userId, existing.id);
        List<ConversationTurn> active = turnMapper.selectList(new QueryWrapper<ConversationTurn>()
                .eq("user_id", userId).eq("session_id", sessionId)
                .in("status", List.of("GENERATING", "PLANNED", "STREAMING", "PARTIAL")));
        boolean supersededBeforeStart = active.stream()
                .anyMatch(turn -> turn.userMessageId != null && turn.userMessageId > userMessageId);
        for (ConversationTurn activeTurn : active) {
            if (activeTurn.userMessageId != null && activeTurn.userMessageId < userMessageId) {
                cancelTurn(userId, activeTurn.id, "USER_INTERRUPTED_BY_NEW_MESSAGE");
            }
        }
        LocalDateTime now = LocalDateTime.now();
        ConversationTurn turn = new ConversationTurn();
        turn.sessionId = sessionId;
        turn.userId = userId;
        turn.userMessageId = userMessageId;
        turn.status = "GENERATING";
        turn.nextEventSequence = 1;
        turn.startedAt = now;
        turnMapper.insert(turn);
        appendEvent(turn, null, null, "TURN_CREATED", null,
                Map.of("sessionId", sessionId, "userMessageId", userMessageId));
        GenerationAttempt attempt = new GenerationAttempt();
        attempt.turnId = turn.id;
        attempt.userId = userId;
        attempt.attemptNumber = 1;
        attempt.status = "RUNNING";
        attempt.startedAt = now;
        attemptMapper.insert(attempt);
        appendEvent(turn, null, null, "GENERATION_STARTED", "turn:" + turn.id, Map.of("attempt", 1));
        turnMapper.updateById(turn);
        if (supersededBeforeStart) {
            return cancelTurn(userId, turn.id, "SUPERSEDED_BEFORE_GENERATION");
        }
        return timeline(userId, turn.id);
    }

    @Override
    @Transactional
    public TurnTimelineVO commitPlan(Long userId, Long turnId, AuroraReplyVO reply) {
        ConversationTurn turn = ownedTurn(userId, turnId, true);
        if (isTerminalCancellation(turn.status)) {
            discardRunningAttempt(turn, "cancelled-before-plan");
            return timeline(userId, turnId);
        }
        if (turn.activePlanId != null) return timeline(userId, turnId);
        LocalDateTime now = LocalDateTime.now();
        TurnPlan plan = new TurnPlan();
        plan.turnId = turn.id;
        plan.userId = userId;
        plan.planVersion = 1;
        plan.commitSlot = 1;
        plan.status = "COMMITTED";
        plan.intent = blankTo(reply == null ? null : reply.detectedTheme, "陪伴与回应");
        plan.posture = blankTo(reply == null ? null : reply.replyTone, "温柔、具体、像朋友");
        plan.stopCondition = "ALL_BUBBLES_COMMITTED_OR_CANCELLED";
        plan.committedAt = now;
        planMapper.insert(plan);
        List<String> messages = reply == null || reply.messages == null ? List.of() : reply.messages;
        appendEvent(turn, plan.id, null, "PLAN_COMMITTED", "turn:" + turn.id,
                Map.of("planVersion", 1, "bubbleCount", messages.size()));
        for (int i = 0; i < messages.size(); i++) {
            MessageBubble bubble = new MessageBubble();
            bubble.turnId = turn.id;
            bubble.planId = plan.id;
            bubble.userId = userId;
            bubble.bubbleOrder = i + 1;
            bubble.purpose = purpose(i, messages.size());
            bubble.content = messages.get(i);
            bubble.status = "PLANNED";
            bubble.sendAfterMs = i == 0 ? 0 : 220;
            bubble.deliveredChars = 0;
            bubble.requiresNoInterruption = i > 0;
            bubble.plannedAt = now;
            bubbleMapper.insert(bubble);
            appendEvent(turn, plan.id, bubble.id, "BUBBLE_PLANNED", "plan:" + plan.id,
                    Map.of("order", i + 1, "purpose", bubble.purpose));
        }
        GenerationAttempt attempt = runningAttempt(turn.id, userId);
        if (attempt != null) {
            attempt.planId = plan.id;
            attempt.status = "COMPLETED";
            attempt.provider = aiState(reply, "provider");
            attempt.modelName = aiState(reply, "model");
            attempt.completedAt = now;
            attemptMapper.updateById(attempt);
        }
        turn.activePlanId = plan.id;
        turn.status = "PLANNED";
        turnMapper.updateById(turn);
        reply.turnId = turn.id;
        reply.planId = plan.id;
        reply.cancelled = false;
        return timeline(userId, turn.id);
    }

    @Override
    @Transactional
    public void recordBubbleProgress(Long userId, Long turnId, int bubbleOrder, int deliveredChars) {
        ConversationTurn turn = ownedTurn(userId, turnId, true);
        MessageBubble bubble = bubbleMapper.selectOne(new QueryWrapper<MessageBubble>()
                .eq("turn_id", turnId).eq("user_id", userId).eq("bubble_order", bubbleOrder).last("LIMIT 1"));
        if (bubble == null || !"PLANNED".equals(bubble.status)) return;
        int bounded = Math.max(0, Math.min(deliveredChars, bubble.content == null ? 0 : bubble.content.length()));
        bubble.deliveredChars = Math.max(bubble.deliveredChars == null ? 0 : bubble.deliveredChars, bounded);
        bubbleMapper.updateById(bubble);
        if (bounded > 0 && bubble.content != null && bounded < bubble.content.length()) {
            appendEvent(turn, bubble.planId, bubble.id, "BUBBLE_PARTIALLY_DELIVERED", "bubble:" + bubble.id,
                    Map.of("deliveredChars", bounded));
            turnMapper.updateById(turn);
        }
    }

    @Override
    @Transactional
    public TurnTimelineVO commitBubble(Long userId, Long turnId, int bubbleOrder, DialogMessage persistedBubble) {
        ConversationTurn turn = ownedTurn(userId, turnId, true);
        if (isTerminalCancellation(turn.status)) return timeline(userId, turnId);
        return commitBubbleLocked(turn, userId, bubbleOrder, persistedBubble);
    }

    @Override
    @Transactional
    public TurnTimelineVO deliverBubble(Long userId, Long turnId, int bubbleOrder,
                                        Supplier<DialogMessage> messagePersistence) {
        ConversationTurn turn = ownedTurn(userId, turnId, true);
        if (isTerminalCancellation(turn.status)) return timeline(userId, turnId);
        if (messagePersistence == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "消息气泡缺少持久化动作");
        }
        MessageBubble bubble = bubbleMapper.selectOne(new QueryWrapper<MessageBubble>()
                .eq("turn_id", turnId).eq("user_id", userId).eq("bubble_order", bubbleOrder).last("LIMIT 1"));
        if (bubble == null) throw new BusinessException(ErrorCode.NOT_FOUND, "消息气泡不存在或不可访问");
        if (!"PLANNED".equals(bubble.status)) return timeline(userId, turnId);
        // The turn row is held FOR UPDATE while the dialog message joins this transaction.
        // A concurrent stop therefore wins before this write or waits until the fully
        // delivered bubble and its choreography state are committed together.
        DialogMessage persistedBubble = messagePersistence.get();
        return commitBubbleLocked(turn, userId, bubbleOrder, persistedBubble);
    }

    private TurnTimelineVO commitBubbleLocked(ConversationTurn turn, Long userId, int bubbleOrder,
                                               DialogMessage persistedBubble) {
        Long turnId = turn.id;
        MessageBubble bubble = bubbleMapper.selectOne(new QueryWrapper<MessageBubble>()
                .eq("turn_id", turnId).eq("user_id", userId).eq("bubble_order", bubbleOrder).last("LIMIT 1"));
        if (bubble == null) throw new BusinessException(ErrorCode.NOT_FOUND, "消息气泡不存在或不可访问");
        if (!"PLANNED".equals(bubble.status)) return timeline(userId, turnId);
        bubble.dialogMessageId = persistedBubble == null ? null : persistedBubble.id;
        bubble.status = "COMMITTED";
        bubble.deliveredChars = bubble.content == null ? 0 : bubble.content.length();
        bubble.sentAt = LocalDateTime.now();
        bubbleMapper.updateById(bubble);
        appendEvent(turn, bubble.planId, bubble.id, "BUBBLE_COMMITTED", "bubble:" + bubble.id,
                Map.of("dialogMessageId", bubble.dialogMessageId == null ? -1L : bubble.dialogMessageId));
        turn.status = "STREAMING";
        turnMapper.updateById(turn);
        return timeline(userId, turn.id);
    }

    @Override
    @Transactional
    public TurnTimelineVO completeTurn(Long userId, Long turnId) {
        ConversationTurn turn = ownedTurn(userId, turnId, true);
        if (isTerminalCancellation(turn.status)) return timeline(userId, turnId);
        long pending = bubbleMapper.selectCount(new QueryWrapper<MessageBubble>()
                .eq("turn_id", turnId).eq("user_id", userId).eq("status", "PLANNED"));
        long committed = bubbleMapper.selectCount(new QueryWrapper<MessageBubble>()
                .eq("turn_id", turnId).eq("user_id", userId).eq("status", "COMMITTED"));
        turn.status = pending == 0 ? (committed == 0 ? "CANCELLED" : "COMPLETED") : "PARTIAL";
        turn.completedAt = LocalDateTime.now();
        appendEvent(turn, turn.activePlanId, null, "TURN_COMPLETED", "turn:" + turn.id,
                Map.of("committedBubbleCount", committed, "pendingBubbleCount", pending));
        turnMapper.updateById(turn);
        return timeline(userId, turn.id);
    }

    @Override
    @Transactional
    public TurnTimelineVO cancelTurn(Long userId, Long turnId, String reason) {
        ConversationTurn turn = ownedTurn(userId, turnId, true);
        if (isTerminalCancellation(turn.status) || "COMPLETED".equals(turn.status)) return timeline(userId, turnId);
        LocalDateTime now = LocalDateTime.now();
        List<MessageBubble> pending = bubbleMapper.selectList(new QueryWrapper<MessageBubble>()
                .eq("turn_id", turnId).eq("user_id", userId).eq("status", "PLANNED"));
        for (MessageBubble bubble : pending) {
            bubble.status = "CANCELLED";
            bubble.cancelledAt = now;
            bubbleMapper.updateById(bubble);
            appendEvent(turn, bubble.planId, bubble.id, "BUBBLE_CANCELLED", "turn:" + turn.id,
                    Map.of("reason", blankTo(reason, "USER_STOPPED"), "order", bubble.bubbleOrder));
        }
        discardRunningAttempt(turn, blankTo(reason, "USER_STOPPED"));
        turn.status = pending.isEmpty() && turn.activePlanId == null ? "CANCELLED" : "INTERRUPTED";
        turn.completedAt = now;
        appendEvent(turn, turn.activePlanId, null, "TURN_INTERRUPTED", "turn:" + turn.id,
                Map.of("reason", blankTo(reason, "USER_STOPPED")));
        turnMapper.updateById(turn);
        return timeline(userId, turn.id);
    }

    @Override
    @Transactional
    public void cancelActiveTurns(Long userId, Long sessionId, String reason) {
        List<ConversationTurn> active = turnMapper.selectList(new QueryWrapper<ConversationTurn>()
                .eq("user_id", userId).eq("session_id", sessionId)
                .in("status", List.of("GENERATING", "PLANNED", "STREAMING", "PARTIAL")));
        for (ConversationTurn turn : active) cancelTurn(userId, turn.id, reason);
    }

    @Override
    public boolean isCancelled(Long userId, Long turnId) {
        ConversationTurn turn = ownedTurn(userId, turnId, false);
        return isTerminalCancellation(turn.status);
    }

    @Override
    @Transactional(readOnly = true)
    public String latestInterruptionContext(Long userId, Long sessionId) {
        ConversationTurn turn = turnMapper.selectOne(new QueryWrapper<ConversationTurn>()
                .eq("user_id", userId).eq("session_id", sessionId)
                .in("status", List.of("INTERRUPTED", "CANCELLED"))
                .orderByDesc("id").last("LIMIT 1"));
        if (turn == null) return "";
        List<MessageBubble> bubbles = bubbleMapper.selectList(new QueryWrapper<MessageBubble>()
                .eq("turn_id", turn.id).eq("user_id", userId).orderByAsc("bubble_order"));
        String delivered = bubbles.stream().map(this::deliveredPart).filter(s -> !s.isBlank())
                .reduce((a, b) -> a + " / " + b).orElse("无");
        String unsent = bubbles.stream().map(this::unsentPart).filter(s -> !s.isBlank())
                .reduce((a, b) -> a + " / " + b).orElse("无");
        return "上一轮被用户自然打断。已说出的内容：" + delivered
                + "。原计划但未发送的内容：" + unsent
                + "。不要重复已说内容，不要假装未发送内容已经被用户听到。";
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
            bubble.deliveredChars = bubble.content == null ? 0 : bubble.content.length();
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

    private ConversationTurn ownedTurn(Long userId, Long turnId, boolean forUpdate) {
        QueryWrapper<ConversationTurn> query = new QueryWrapper<ConversationTurn>()
                .eq("id", turnId).eq("user_id", userId);
        query.last(forUpdate ? "LIMIT 1 FOR UPDATE" : "LIMIT 1");
        ConversationTurn turn = turnMapper.selectOne(query);
        if (turn == null) throw new BusinessException(ErrorCode.NOT_FOUND, "对话时间线不存在或不可访问");
        return turn;
    }

    private GenerationAttempt runningAttempt(Long turnId, Long userId) {
        return attemptMapper.selectOne(new QueryWrapper<GenerationAttempt>()
                .eq("turn_id", turnId).eq("user_id", userId).eq("status", "RUNNING").last("LIMIT 1"));
    }

    private void discardRunningAttempt(ConversationTurn turn, String reason) {
        GenerationAttempt attempt = runningAttempt(turn.id, turn.userId);
        if (attempt == null) return;
        attempt.status = "DISCARDED";
        attempt.completedAt = LocalDateTime.now();
        attemptMapper.updateById(attempt);
        appendEvent(turn, null, null, "GENERATION_DISCARDED", "turn:" + turn.id,
                Map.of("reason", blankTo(reason, "cancelled")));
    }

    private boolean isTerminalCancellation(String status) {
        return "CANCELLED".equals(status) || "INTERRUPTED".equals(status);
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

    private String deliveredPart(MessageBubble bubble) {
        if (bubble.content == null) return "";
        if ("COMMITTED".equals(bubble.status)) return bubble.content;
        int chars = Math.max(0, Math.min(bubble.deliveredChars == null ? 0 : bubble.deliveredChars, bubble.content.length()));
        return bubble.content.substring(0, chars);
    }

    private String unsentPart(MessageBubble bubble) {
        if (bubble.content == null || "COMMITTED".equals(bubble.status)) return "";
        int chars = Math.max(0, Math.min(bubble.deliveredChars == null ? 0 : bubble.deliveredChars, bubble.content.length()));
        return bubble.content.substring(chars);
    }
}
