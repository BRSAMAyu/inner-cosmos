package com.innercosmos.conversation.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.conversation.entity.ConversationEvent;
import com.innercosmos.conversation.entity.ConversationTurn;
import com.innercosmos.conversation.entity.GenerationAttempt;
import com.innercosmos.conversation.entity.MessageBubble;
import com.innercosmos.conversation.entity.TurnPlan;
import com.innercosmos.conversation.entity.TurnDeliberationSnapshot;
import com.innercosmos.conversation.entity.TurnGenerationRequest;
import com.innercosmos.conversation.vo.TurnTimelineVO;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.ConversationEventMapper;
import com.innercosmos.mapper.ConversationTurnMapper;
import com.innercosmos.mapper.GenerationAttemptMapper;
import com.innercosmos.mapper.MessageBubbleMapper;
import com.innercosmos.mapper.TurnPlanMapper;
import com.innercosmos.mapper.TurnDeliberationSnapshotMapper;
import com.innercosmos.mapper.TurnGenerationRequestMapper;
import com.innercosmos.vo.AuroraReplyVO;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
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
    private final Clock clock;
    private final TurnDeliberationSnapshotMapper deliberationMapper;
    private final TurnGenerationRequestMapper generationRequestMapper;
    private final InterruptionDeltaBuilder interruptionDeltaBuilder;

    public ConversationChoreographyServiceImpl(ConversationTurnMapper turnMapper,
                                                TurnPlanMapper planMapper,
                                                MessageBubbleMapper bubbleMapper,
                                                ConversationEventMapper eventMapper,
                                                GenerationAttemptMapper attemptMapper,
                                                TurnDeliberationSnapshotMapper deliberationMapper,
                                                TurnGenerationRequestMapper generationRequestMapper,
                                                ObjectMapper objectMapper,
                                                Clock clock,
                                                InterruptionDeltaBuilder interruptionDeltaBuilder) {
        this.turnMapper = turnMapper;
        this.planMapper = planMapper;
        this.bubbleMapper = bubbleMapper;
        this.eventMapper = eventMapper;
        this.attemptMapper = attemptMapper;
        this.deliberationMapper = deliberationMapper;
        this.generationRequestMapper = generationRequestMapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.interruptionDeltaBuilder = interruptionDeltaBuilder;
    }

    @Override
    @Transactional
    public TurnTimelineVO stageDeliberation(Long userId, Long turnId,
                                            int expectedPriorRevision,
                                            String userSafeSnapshotJson) {
        ConversationTurn turn = ownedTurn(userId, turnId, true);
        if (isTerminal(turn.status)) return timeline(userId, turnId);
        String snapshot = validateUserSafeSnapshot(userSafeSnapshotJson);
        TurnDeliberationSnapshot latest = deliberationMapper.selectOne(
                new QueryWrapper<TurnDeliberationSnapshot>()
                        .eq("turn_id", turnId).eq("user_id", userId)
                        .orderByDesc("plan_revision").last("LIMIT 1"));
        int currentRevision = latest == null || latest.planRevision == null ? 0 : latest.planRevision;
        if (currentRevision != expectedPriorRevision) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "Deliberation revision was superseded by another planner");
        }
        TurnDeliberationSnapshot staged = new TurnDeliberationSnapshot();
        staged.turnId = turnId;
        staged.userId = userId;
        staged.planRevision = currentRevision + 1;
        staged.status = "STAGED";
        staged.snapshotJson = snapshot;
        deliberationMapper.insert(staged);
        appendEvent(turn, null, null, "DELIBERATION_STAGED", "turn:" + turn.id,
                Map.of("planRevision", staged.planRevision));
        turnMapper.updateById(turn);
        return timeline(userId, turnId);
    }

    @Override
    @Transactional
    public TurnTimelineVO stageDeliberationFenced(
            Long userId, Long turnId, int expectedPriorRevision, String userSafeSnapshotJson,
            String owner, long fencingToken, Duration ttl) {
        ConversationTurn turn = ownedTurn(userId, turnId, true);
        requireLease(turn, owner, fencingToken);
        turn.leaseExpiresAt = utcNow().plus(validTtl(ttl));
        turnMapper.updateById(turn);
        return stageDeliberation(userId, turnId, expectedPriorRevision, userSafeSnapshotJson);
    }

    @Override
    @Transactional
    public GenerationRequestSnapshot stageGenerationRequest(
            Long userId, Long turnId, Long sessionId, Long userMessageId,
            String mode, String locale, String region, String timezone,
            String contextVersion, boolean foregroundAcknowledgementSent) {
        if (userId == null || turnId == null || sessionId == null || userMessageId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Generation request snapshot requires turn and message references");
        }
        ConversationTurn turn = ownedTurn(userId, turnId, true);
        if (!userId.equals(turn.userId) || !sessionId.equals(turn.sessionId)
                || !userMessageId.equals(turn.userMessageId)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "Generation request references do not match the authoritative turn");
        }
        TurnGenerationRequest existing = generationRequestMapper.selectOne(
                new QueryWrapper<TurnGenerationRequest>()
                        .eq("turn_id", turnId).eq("user_id", userId).last("LIMIT 1"));
        TurnGenerationRequest candidate = new TurnGenerationRequest();
        candidate.turnId = turnId;
        candidate.userId = userId;
        candidate.sessionId = sessionId;
        candidate.userMessageId = userMessageId;
        candidate.mode = bounded(mode, 32, "DAILY_TALK");
        candidate.locale = bounded(locale, 24, null);
        candidate.region = bounded(region, 16, null);
        candidate.timezone = bounded(timezone, 64, null);
        candidate.contextVersion = bounded(contextVersion, 48, "aurora-context.v1");
        candidate.foregroundAcknowledgementSent = foregroundAcknowledgementSent;
        if (existing != null) {
            if (!sameGenerationRequest(existing, candidate)) {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "Generation request snapshot is immutable once staged");
            }
            return toGenerationSnapshot(existing);
        }
        generationRequestMapper.insert(candidate);
        appendEvent(turn, null, null, "GENERATION_REQUEST_STAGED", "turn:" + turn.id,
                Map.of("contextVersion", candidate.contextVersion));
        turnMapper.updateById(turn);
        return toGenerationSnapshot(candidate);
    }

    @Override
    @Transactional(readOnly = true)
    public GenerationRequestSnapshot generationRequest(Long userId, Long turnId) {
        ownedTurn(userId, turnId, false);
        TurnGenerationRequest request = generationRequestMapper.selectOne(
                new QueryWrapper<TurnGenerationRequest>()
                        .eq("turn_id", turnId).eq("user_id", userId).last("LIMIT 1"));
        return request == null ? null : toGenerationSnapshot(request);
    }

    @Override
    @Transactional
    public DeliveryLease claimGenerationLease(
            Long userId, Long turnId, String owner, Duration ttl) {
        ConversationTurn turn = ownedTurn(userId, turnId, true);
        if (isTerminal(turn.status) || turn.activePlanId != null
                || owner == null || owner.isBlank()
                || generationRequestMapper.selectCount(new QueryWrapper<TurnGenerationRequest>()
                        .eq("turn_id", turnId).eq("user_id", userId)) == 0) {
            return null;
        }
        LocalDateTime now = utcNow();
        if (validLease(turn, owner, turn.leaseToken == null ? -1L : turn.leaseToken, now)) {
            return new DeliveryLease(owner, turn.leaseToken, turn.leaseExpiresAt);
        }
        if (turn.leaseExpiresAt != null && turn.leaseExpiresAt.isAfter(now)) return null;
        long nextToken = (turn.leaseToken == null ? 0L : turn.leaseToken) + 1L;
        turn.leaseOwner = owner;
        turn.leaseToken = nextToken;
        turn.leaseExpiresAt = now.plus(validTtl(ttl));
        turnMapper.updateById(turn);
        appendEvent(turn, null, null, "GENERATION_LEASE_CLAIMED", "turn:" + turn.id,
                Map.of("fencingToken", nextToken));
        turnMapper.updateById(turn);
        return new DeliveryLease(owner, nextToken, turn.leaseExpiresAt);
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
        copyProposedAction(plan, reply);
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
        recordBubbleProgressLocked(turn, userId, bubbleOrder, deliveredChars);
    }

    private void recordBubbleProgressLocked(ConversationTurn turn, Long userId,
                                            int bubbleOrder, int deliveredChars) {
        MessageBubble bubble = bubbleMapper.selectOne(new QueryWrapper<MessageBubble>()
                .eq("turn_id", turn.id).eq("user_id", userId).eq("bubble_order", bubbleOrder).last("LIMIT 1"));
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
    public DeliveryLease claimDeliveryLease(Long userId, Long turnId, String owner, Duration ttl) {
        ConversationTurn turn = ownedTurn(userId, turnId, true);
        if (isTerminal(turn.status) || turn.activePlanId == null || owner == null || owner.isBlank()) {
            return null;
        }
        LocalDateTime now = utcNow();
        if (turn.leaseExpiresAt != null && turn.leaseExpiresAt.isAfter(now)
                && turn.leaseOwner != null && !owner.equals(turn.leaseOwner)) {
            return null;
        }
        long nextToken = (turn.leaseToken == null ? 0L : turn.leaseToken) + 1L;
        turn.leaseOwner = owner;
        turn.leaseToken = nextToken;
        turn.leaseExpiresAt = now.plus(validTtl(ttl));
        turnMapper.updateById(turn);
        appendEvent(turn, turn.activePlanId, null, "DELIVERY_LEASE_CLAIMED", "turn:" + turn.id,
                Map.of("fencingToken", nextToken));
        turnMapper.updateById(turn);
        return new DeliveryLease(owner, nextToken, turn.leaseExpiresAt);
    }

    @Override
    @Transactional
    public boolean renewDeliveryLease(Long userId, Long turnId, String owner,
                                      long fencingToken, Duration ttl) {
        ConversationTurn turn = ownedTurn(userId, turnId, true);
        if (!validLease(turn, owner, fencingToken, utcNow())) return false;
        turn.leaseExpiresAt = utcNow().plus(validTtl(ttl));
        turnMapper.updateById(turn);
        return true;
    }

    @Override
    @Transactional
    public void recordBubbleProgressFenced(Long userId, Long turnId, int bubbleOrder,
                                           int deliveredChars, String owner,
                                           long fencingToken, Duration ttl) {
        ConversationTurn turn = ownedTurn(userId, turnId, true);
        requireLease(turn, owner, fencingToken);
        turn.leaseExpiresAt = utcNow().plus(validTtl(ttl));
        recordBubbleProgressLocked(turn, userId, bubbleOrder, deliveredChars);
        turnMapper.updateById(turn);
    }

    @Override
    @Transactional
    public TurnTimelineVO deliverBubbleFenced(Long userId, Long turnId, int bubbleOrder,
                                              String owner, long fencingToken, Duration ttl,
                                              Supplier<DialogMessage> messagePersistence) {
        ConversationTurn turn = ownedTurn(userId, turnId, true);
        requireLease(turn, owner, fencingToken);
        turn.leaseExpiresAt = utcNow().plus(validTtl(ttl));
        if (isTerminalCancellation(turn.status)) return timeline(userId, turnId);
        if (messagePersistence == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "消息气泡缺少持久化动作");
        }
        MessageBubble bubble = bubbleMapper.selectOne(new QueryWrapper<MessageBubble>()
                .eq("turn_id", turnId).eq("user_id", userId)
                .eq("bubble_order", bubbleOrder).last("LIMIT 1"));
        if (bubble == null) throw new BusinessException(ErrorCode.NOT_FOUND, "消息气泡不存在或不可访问");
        if (!"PLANNED".equals(bubble.status)) return timeline(userId, turnId);
        return commitBubbleLocked(turn, userId, bubbleOrder, messagePersistence.get());
    }

    @Override
    @Transactional
    public TurnTimelineVO completeTurnFenced(Long userId, Long turnId, String owner,
                                             long fencingToken) {
        ConversationTurn turn = ownedTurn(userId, turnId, true);
        requireLease(turn, owner, fencingToken);
        if (isTerminalCancellation(turn.status)) return timeline(userId, turnId);
        long pending = bubbleMapper.selectCount(new QueryWrapper<MessageBubble>()
                .eq("turn_id", turnId).eq("user_id", userId).eq("status", "PLANNED"));
        long committed = bubbleMapper.selectCount(new QueryWrapper<MessageBubble>()
                .eq("turn_id", turnId).eq("user_id", userId).eq("status", "COMMITTED"));
        turn.status = pending == 0 ? (committed == 0 ? "CANCELLED" : "COMPLETED") : "PARTIAL";
        turn.completedAt = LocalDateTime.now();
        turn.leaseExpiresAt = utcNow();
        appendEvent(turn, turn.activePlanId, null, "TURN_COMPLETED", "turn:" + turn.id,
                Map.of("committedBubbleCount", committed, "pendingBubbleCount", pending,
                        "fencingToken", fencingToken));
        turnMapper.updateById(turn);
        return timeline(userId, turn.id);
    }

    @Override
    @Transactional
    public TurnTimelineVO commitPlanFenced(Long userId, Long turnId, AuroraReplyVO reply,
                                           String owner, long fencingToken) {
        ConversationTurn turn = ownedTurn(userId, turnId, true);
        requireLease(turn, owner, fencingToken);
        TurnTimelineVO committed = commitPlan(userId, turnId, reply);
        if (committed.activePlan != null) {
            // Generation authority has fulfilled its only purpose. Clear the owner/expiry
            // explicitly instead of writing "now": JDBC timestamp precision may round that value
            // slightly into the future. Use a conditional SQL update rather than a second entity
            // read: MyBatis' transaction-local cache may otherwise return the pre-plan turn object
            // and skip the release branch. The monotonic token remains as fencing history.
            turnMapper.update(null, new UpdateWrapper<ConversationTurn>()
                    .eq("id", turnId)
                    .eq("user_id", userId)
                    .eq("lease_owner", owner)
                    .eq("lease_token", fencingToken)
                    .set("lease_owner", null)
                    .set("lease_expires_at", null));
        }
        return committed;
    }

    @Override
    @Transactional
    public TurnTimelineVO failUnrecoverableGeneration(Long userId, Long turnId,
                                                       LocalDateTime cutoff, String reason) {
        ConversationTurn turn = ownedTurn(userId, turnId, true);
        LocalDateTime lastProgress = turn.updatedAt == null ? turn.startedAt : turn.updatedAt;
        if (turn.activePlanId != null || lastProgress == null || cutoff == null
                || lastProgress.isAfter(cutoff) || isTerminal(turn.status)) {
            return timeline(userId, turnId);
        }
        discardRunningAttempt(turn, blankTo(reason, "GENERATION_NOT_RESUMABLE"));
        turn.status = "FAILED";
        turn.completedAt = LocalDateTime.now();
        appendEvent(turn, null, null, "TURN_FAILED", "turn:" + turn.id,
                Map.of("reason", blankTo(reason, "GENERATION_NOT_RESUMABLE"),
                        "resumable", false));
        turnMapper.updateById(turn);
        return timeline(userId, turn.id);
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
        return cancelTurnLocked(turn, userId, reason);
    }

    @Override
    @Transactional
    public TurnTimelineVO interruptIfStale(Long userId, Long turnId, LocalDateTime cutoff, String reason) {
        ConversationTurn turn = ownedTurn(userId, turnId, true);
        LocalDateTime lastProgress = turn.updatedAt == null ? turn.startedAt : turn.updatedAt;
        if (lastProgress == null || cutoff == null || lastProgress.isAfter(cutoff)
                || isTerminalCancellation(turn.status) || "COMPLETED".equals(turn.status)) {
            return timeline(userId, turnId);
        }
        return cancelTurnLocked(turn, userId, blankTo(reason, "STREAM_ORPHANED"));
    }

    private TurnTimelineVO cancelTurnLocked(ConversationTurn turn, Long userId, String reason) {
        Long turnId = turn.id;
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
        return latestInterruptionContext(userId, sessionId, "");
    }

    @Override
    @Transactional(readOnly = true)
    public String latestInterruptionContext(Long userId, Long sessionId, String newUserMessage) {
        ConversationTurn turn = turnMapper.selectOne(new QueryWrapper<ConversationTurn>()
                .eq("user_id", userId).eq("session_id", sessionId)
                .in("status", List.of("INTERRUPTED", "CANCELLED"))
                .orderByDesc("id").last("LIMIT 1"));
        if (turn == null) return "";
        return serialize(interruptionDeltaBuilder.build(timeline(userId, turn.id), newUserMessage));
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
        copyProposedAction(plan, reply);
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
        vo.deliberations = deliberationMapper.selectList(new QueryWrapper<TurnDeliberationSnapshot>()
                .eq("turn_id", turn.id).eq("user_id", userId).orderByAsc("plan_revision"));
        if (isTerminalCancellation(turn.status)) {
            vo.interruptionDelta = interruptionDeltaBuilder.build(vo, "");
        }
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

    private void copyProposedAction(TurnPlan plan, AuroraReplyVO reply) {
        if (plan == null || reply == null || reply.proposedActionType == null
                || reply.proposedActionType.isBlank()) return;
        plan.proposedActionType = reply.proposedActionType;
        plan.proposedActionPayload = reply.proposedActionPayloadJson;
        plan.proposedActionSummary = reply.proposedActionSummary;
        plan.actionStatus = blankTo(reply.proposedActionStatus, "PENDING_CONFIRMATION");
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
        return "CANCELLED".equals(status) || "INTERRUPTED".equals(status)
                || "FAILED".equals(status);
    }

    private boolean isTerminal(String status) {
        return isTerminalCancellation(status) || "COMPLETED".equals(status) || "FAILED".equals(status);
    }

    private void requireLease(ConversationTurn turn, String owner, long fencingToken) {
        if (!validLease(turn, owner, fencingToken, utcNow())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "Aurora turn delivery lease was superseded by another runtime");
        }
    }

    private boolean validLease(ConversationTurn turn, String owner, long fencingToken,
                               LocalDateTime now) {
        return turn != null && owner != null && owner.equals(turn.leaseOwner)
                && turn.leaseToken != null && turn.leaseToken == fencingToken
                && turn.leaseExpiresAt != null && turn.leaseExpiresAt.isAfter(now);
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private Duration validTtl(Duration ttl) {
        return ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofSeconds(15) : ttl;
    }

    private GenerationRequestSnapshot toGenerationSnapshot(TurnGenerationRequest request) {
        return new GenerationRequestSnapshot(
                request.turnId, request.sessionId, request.userMessageId,
                request.mode, request.locale, request.region, request.timezone,
                request.contextVersion,
                Boolean.TRUE.equals(request.foregroundAcknowledgementSent));
    }

    private boolean sameGenerationRequest(TurnGenerationRequest left, TurnGenerationRequest right) {
        return java.util.Objects.equals(left.turnId, right.turnId)
                && java.util.Objects.equals(left.userId, right.userId)
                && java.util.Objects.equals(left.sessionId, right.sessionId)
                && java.util.Objects.equals(left.userMessageId, right.userMessageId)
                && java.util.Objects.equals(left.mode, right.mode)
                && java.util.Objects.equals(left.locale, right.locale)
                && java.util.Objects.equals(left.region, right.region)
                && java.util.Objects.equals(left.timezone, right.timezone)
                && java.util.Objects.equals(left.contextVersion, right.contextVersion)
                && java.util.Objects.equals(
                        Boolean.TRUE.equals(left.foregroundAcknowledgementSent),
                        Boolean.TRUE.equals(right.foregroundAcknowledgementSent));
    }

    private String bounded(String value, int maxLength, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Generation request metadata exceeds its bounded column");
        }
        return normalized;
    }

    private String json(Map<String, ?> payload) {
        try {
            return objectMapper.writeValueAsString(new LinkedHashMap<>(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("conversation event payload serialization failed", e);
        }
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("interruption delta serialization failed", exception);
        }
    }

    private String validateUserSafeSnapshot(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 32_768) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Deliberation snapshot must be a bounded JSON object");
        }
        try {
            var node = objectMapper.readTree(raw);
            if (!node.isObject()) throw new IllegalArgumentException("object required");
            for (String forbidden : List.of(
                    "chainOfThought", "chain_of_thought", "rawReasoning",
                    "reasoningTokens", "rawPrompt", "hiddenReasoning")) {
                if (node.has(forbidden)) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST,
                            "Deliberation snapshot contains a forbidden hidden-reasoning field");
                }
            }
            return objectMapper.writeValueAsString(node);
        } catch (BusinessException rejected) {
            throw rejected;
        } catch (Exception malformed) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Deliberation snapshot must be valid JSON");
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
