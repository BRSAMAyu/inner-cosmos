package com.innercosmos.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innercosmos.conversation.entity.TurnPlan;
import com.innercosmos.conversation.service.ConversationChoreographyService;
import com.innercosmos.conversation.vo.TurnTimelineVO;
import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.DialogSession;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.DialogMessageMapper;
import com.innercosmos.mapper.DialogSessionMapper;
import com.innercosmos.mapper.TurnPlanMapper;
import com.innercosmos.mapper.ConversationTurnMapper;
import com.innercosmos.vo.AuroraReplyVO;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"llm.provider=mock", "llm.mode=dev"})
@Transactional
class ConversationChoreographyIntegrationTest {
    @Autowired ConversationChoreographyService choreography;
    @Autowired DialogSessionMapper sessionMapper;
    @Autowired DialogMessageMapper messageMapper;
    @Autowired TurnPlanMapper planMapper;
    @Autowired ConversationTurnMapper turnMapper;

    @Test
    void persistsReplayableOneToThreeBubbleLifecycleWithoutChangingContent() {
        Fixture fixture = fixture(81001L, List.of("我在。", "这份疲惫像是撑了很久。", "先只做一个很小的动作，好吗？"));

        TurnTimelineVO timeline = choreography.recordCompletedTurn(
                fixture.userId, fixture.session.id, fixture.userMessage.id, fixture.reply, fixture.auroraMessages);

        assertThat(timeline.turn.status).isEqualTo("COMPLETED");
        assertThat(timeline.activePlan.status).isEqualTo("COMMITTED");
        assertThat(timeline.bubbles).extracting(b -> b.content)
                .containsExactlyElementsOf(fixture.reply.messages);
        assertThat(timeline.bubbles).extracting(b -> b.bubbleOrder).containsExactly(1, 2, 3);
        assertThat(timeline.bubbles).extracting(b -> b.purpose)
                .containsExactly("ACKNOWLEDGE", "DEEPEN", "GENTLE_NEXT_STEP");
        assertThat(timeline.events).extracting(e -> e.eventType)
                .containsExactly("TURN_CREATED", "PLAN_COMMITTED",
                        "BUBBLE_PLANNED", "BUBBLE_COMMITTED",
                        "BUBBLE_PLANNED", "BUBBLE_COMMITTED",
                        "BUBBLE_PLANNED", "BUBBLE_COMMITTED", "TURN_COMPLETED");
        assertThat(timeline.events).extracting(e -> e.eventSequence)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertThat(timeline.generationAttempts).hasSize(1);
    }

    @Test
    void persistsConfirmationGatedActionOnTheCommittedTurnPlan() {
        Fixture fixture = fixture(81000L, List.of("请确认后再保存。"));
        fixture.reply.proposedActionType = "REMEMBER";
        fixture.reply.proposedActionSummary = "保存为私密记忆";
        fixture.reply.proposedActionPayloadJson = "{\"title\":\"展示\",\"content\":\"先走本地备用流程\"}";
        fixture.reply.proposedActionStatus = "PENDING_CONFIRMATION";

        TurnTimelineVO timeline = choreography.recordCompletedTurn(
                fixture.userId, fixture.session.id, fixture.userMessage.id, fixture.reply, fixture.auroraMessages);

        assertThat(timeline.activePlan.proposedActionType).isEqualTo("REMEMBER");
        assertThat(timeline.activePlan.proposedActionPayload).contains("先走本地备用流程");
        assertThat(timeline.activePlan.actionStatus).isEqualTo("PENDING_CONFIRMATION");
        assertThat(timeline.activePlan.actionConfirmedAt).isNull();
    }

    @Test
    void retryReturnsSameCommittedPlanAndNeverDuplicatesBubbles() {
        Fixture fixture = fixture(81002L, List.of("第一条", "第二条"));
        TurnTimelineVO first = choreography.recordCompletedTurn(
                fixture.userId, fixture.session.id, fixture.userMessage.id, fixture.reply, fixture.auroraMessages);
        TurnTimelineVO retry = choreography.recordCompletedTurn(
                fixture.userId, fixture.session.id, fixture.userMessage.id, fixture.reply, fixture.auroraMessages);

        assertThat(retry.turn.id).isEqualTo(first.turn.id);
        assertThat(retry.activePlan.id).isEqualTo(first.activePlan.id);
        assertThat(retry.bubbles).hasSize(2);
        assertThat(retry.events).hasSize(7);
    }

    @Test
    void databaseRejectsSecondEffectivePlanCommitForSameTurn() {
        Fixture fixture = fixture(81003L, List.of("唯一回应"));
        TurnTimelineVO committed = choreography.recordCompletedTurn(
                fixture.userId, fixture.session.id, fixture.userMessage.id, fixture.reply, fixture.auroraMessages);

        TurnPlan duplicate = new TurnPlan();
        duplicate.turnId = committed.turn.id;
        duplicate.userId = fixture.userId;
        duplicate.planVersion = 2;
        duplicate.commitSlot = 1;
        duplicate.status = "COMMITTED";
        duplicate.committedAt = LocalDateTime.now();
        assertThatThrownBy(() -> planMapper.insert(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void timelineIsOwnerScopedAndOpaqueToOtherUsers() {
        Fixture fixture = fixture(81004L, List.of("只属于你"));
        TurnTimelineVO committed = choreography.recordCompletedTurn(
                fixture.userId, fixture.session.id, fixture.userMessage.id, fixture.reply, fixture.auroraMessages);

        assertThatThrownBy(() -> choreography.timeline(999999L, committed.turn.id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在或不可访问");
    }

    @Test
    void stopDuringProviderGenerationDiscardsAttemptAndCommitsNoPlanOrBubble() {
        Fixture fixture = fixture(81005L, List.of("这条模型结果不应落库"));
        TurnTimelineVO started = choreography.beginTurn(
                fixture.userId, fixture.session.id, fixture.userMessage.id);

        TurnTimelineVO stopped = choreography.cancelTurn(
                fixture.userId, started.turn.id, "USER_STOPPED");
        TurnTimelineVO lateProviderResult = choreography.commitPlan(
                fixture.userId, started.turn.id, fixture.reply);

        assertThat(stopped.turn.status).isEqualTo("CANCELLED");
        assertThat(lateProviderResult.activePlan).isNull();
        assertThat(lateProviderResult.bubbles).isEmpty();
        assertThat(lateProviderResult.generationAttempts).singleElement()
                .extracting(a -> a.status).isEqualTo("DISCARDED");
        assertThat(lateProviderResult.events).extracting(e -> e.eventType)
                .contains("GENERATION_DISCARDED", "TURN_INTERRUPTED");
    }

    @Test
    void stopAfterFirstBubbleKeepsDeliveredContextAndCancelsEveryPendingBubble() {
        Fixture fixture = fixture(81006L, List.of("已经说出的第一条", "不再发送的第二条", "不再发送的第三条"));
        TurnTimelineVO started = choreography.beginTurn(
                fixture.userId, fixture.session.id, fixture.userMessage.id);
        TurnTimelineVO planned = choreography.commitPlan(fixture.userId, started.turn.id, fixture.reply);
        choreography.commitBubble(fixture.userId, started.turn.id, 1, fixture.auroraMessages.get(0));
        choreography.recordBubbleProgress(fixture.userId, started.turn.id, 2, 4);

        TurnTimelineVO stopped = choreography.cancelTurn(fixture.userId, started.turn.id, "USER_INTERRUPTED");

        assertThat(planned.bubbles).hasSize(3);
        assertThat(stopped.turn.status).isEqualTo("INTERRUPTED");
        assertThat(stopped.bubbles).extracting(b -> b.status)
                .containsExactly("COMMITTED", "CANCELLED", "CANCELLED");
        assertThat(stopped.bubbles).extracting(b -> b.content)
                .containsExactlyElementsOf(fixture.reply.messages);
        assertThat(stopped.events).extracting(e -> e.eventType)
                .contains("BUBBLE_COMMITTED", "BUBBLE_CANCELLED", "TURN_INTERRUPTED");
        String interruption = choreography.latestInterruptionContext(
                fixture.userId, fixture.session.id, "等等，我现在想说另一件事");
        assertThat(interruption)
                .contains("\"deliveredContent\"")
                .contains("\"cancelledBubblePurposes\"")
                .contains("\"newUserMessage\":\"等等，我现在想说另一件事\"")
                .contains("\"continuityDecision\":\"MERGE\"")
                .contains("\"changedFacts\"")
                .contains("\"mustNotRepeat\"")
                .doesNotContain("的第二条", "不再发送的第三条");
        assertThat(stopped.interruptionDelta).isNotNull();
        assertThat(stopped.interruptionDelta.deliveredContent).isNotEmpty();
    }

    @Test
    void newMessageCancelsPriorActiveTurnBeforeBeginningReplanTurn() {
        Fixture fixture = fixture(81007L, List.of("旧计划"));
        TurnTimelineVO first = choreography.beginTurn(
                fixture.userId, fixture.session.id, fixture.userMessage.id);
        choreography.commitPlan(fixture.userId, first.turn.id, fixture.reply);

        choreography.cancelActiveTurns(fixture.userId, fixture.session.id, "USER_INTERRUPTED_BY_NEW_MESSAGE");
        DialogMessage nextUserMessage = message(fixture.session.id, fixture.userId, "USER", "等等，我真正想说的是另一件事");
        messageMapper.insert(nextUserMessage);
        TurnTimelineVO replanned = choreography.beginTurn(fixture.userId, fixture.session.id, nextUserMessage.id);

        assertThat(choreography.timeline(fixture.userId, first.turn.id).turn.status).isEqualTo("INTERRUPTED");
        assertThat(replanned.turn.status).isEqualTo("GENERATING");
        assertThat(replanned.turn.id).isNotEqualTo(first.turn.id);
    }

    @Test
    void cancelledTurnNeverInvokesAtomicMessagePersistence() {
        Fixture fixture = fixture(81008L, List.of("停止后绝不能写入"));
        TurnTimelineVO started = choreography.beginTurn(
                fixture.userId, fixture.session.id, fixture.userMessage.id);
        choreography.commitPlan(fixture.userId, started.turn.id, fixture.reply);
        choreography.cancelTurn(fixture.userId, started.turn.id, "USER_STOPPED");
        AtomicBoolean invoked = new AtomicBoolean(false);

        TurnTimelineVO result = choreography.deliverBubble(fixture.userId, started.turn.id, 1, () -> {
            invoked.set(true);
            DialogMessage message = message(fixture.session.id, fixture.userId, "AURORA", "不应出现");
            messageMapper.insert(message);
            return message;
        });

        assertThat(invoked).isFalse();
        assertThat(result.turn.status).isEqualTo("INTERRUPTED");
        assertThat(result.bubbles).singleElement().extracting(b -> b.status).isEqualTo("CANCELLED");
    }

    @Test
    void committedBubbleRetryNeverInvokesPersistenceTwice() {
        Fixture fixture = fixture(81009L, List.of("只能写入一次"));
        TurnTimelineVO started = choreography.beginTurn(
                fixture.userId, fixture.session.id, fixture.userMessage.id);
        choreography.commitPlan(fixture.userId, started.turn.id, fixture.reply);
        choreography.commitBubble(fixture.userId, started.turn.id, 1, fixture.auroraMessages.get(0));
        AtomicBoolean invoked = new AtomicBoolean(false);

        TurnTimelineVO result = choreography.deliverBubble(fixture.userId, started.turn.id, 1, () -> {
            invoked.set(true);
            throw new AssertionError("duplicate persistence must not run");
        });

        assertThat(invoked).isFalse();
        assertThat(result.bubbles).singleElement().extracting(b -> b.status).isEqualTo("COMMITTED");
    }

    @Test
    void expiredLeaseIsTakenOverWithHigherFenceAndOldPodCannotCommit() {
        Fixture fixture = fixture(81010L, List.of("已经安全生成，等待接管交付"));
        TurnTimelineVO started = choreography.beginTurn(
                fixture.userId, fixture.session.id, fixture.userMessage.id);
        choreography.commitPlan(fixture.userId, started.turn.id, fixture.reply);
        var first = choreography.claimDeliveryLease(
                fixture.userId, started.turn.id, "pod-a", Duration.ofSeconds(30));

        var persisted = turnMapper.selectById(started.turn.id);
        persisted.leaseExpiresAt = LocalDateTime.ofInstant(
                Instant.now().minusSeconds(1), ZoneOffset.UTC);
        turnMapper.updateById(persisted);
        var takeover = choreography.claimDeliveryLease(
                fixture.userId, started.turn.id, "pod-b", Duration.ofSeconds(30));

        assertThat(takeover.fencingToken()).isGreaterThan(first.fencingToken());
        AtomicBoolean staleSupplierInvoked = new AtomicBoolean(false);
        assertThatThrownBy(() -> choreography.deliverBubbleFenced(
                fixture.userId, started.turn.id, 1,
                first.owner(), first.fencingToken(), Duration.ofSeconds(30), () -> {
                    staleSupplierInvoked.set(true);
                    return fixture.auroraMessages.getFirst();
                })).isInstanceOf(BusinessException.class)
                .hasMessageContaining("superseded");
        assertThat(staleSupplierInvoked).isFalse();

        TurnTimelineVO delivered = choreography.deliverBubbleFenced(
                fixture.userId, started.turn.id, 1,
                takeover.owner(), takeover.fencingToken(), Duration.ofSeconds(30),
                () -> fixture.auroraMessages.getFirst());
        TurnTimelineVO completed = choreography.completeTurnFenced(
                fixture.userId, started.turn.id, takeover.owner(), takeover.fencingToken());

        assertThat(delivered.bubbles).singleElement()
                .extracting(b -> b.status).isEqualTo("COMMITTED");
        assertThat(completed.turn.status).isEqualTo("COMPLETED");
        assertThat(completed.events).extracting(e -> e.eventType)
                .contains("DELIVERY_LEASE_CLAIMED", "BUBBLE_COMMITTED", "TURN_COMPLETED");
    }

    @Test
    void secondPodCannotClaimAnUnexpiredAuthoritativeLease() {
        Fixture fixture = fixture(81011L, List.of("同一权威回合"));
        TurnTimelineVO started = choreography.beginTurn(
                fixture.userId, fixture.session.id, fixture.userMessage.id);
        choreography.commitPlan(fixture.userId, started.turn.id, fixture.reply);

        var first = choreography.claimDeliveryLease(
                fixture.userId, started.turn.id, "pod-a", Duration.ofSeconds(30));
        var competing = choreography.claimDeliveryLease(
                fixture.userId, started.turn.id, "pod-b", Duration.ofSeconds(30));

        assertThat(first).isNotNull();
        assertThat(competing).isNull();
        assertThat(choreography.timeline(fixture.userId, started.turn.id).turn.leaseOwner)
                .isEqualTo("pod-a");
    }

    @Test
    void providerCrashBeforeFirstAssistantContentRegeneratesUnderOneGenerationFence() {
        Fixture fixture = fixture(81014L, List.of("鍙湁鎺ョ Pod 鍙互鎻愪氦杩欎釜璁″垝"));
        TurnTimelineVO started = choreography.beginTurn(
                fixture.userId, fixture.session.id, fixture.userMessage.id);
        var snapshot = choreography.stageGenerationRequest(
                fixture.userId, started.turn.id, fixture.session.id, fixture.userMessage.id,
                "DAILY_TALK", "zh-CN", "CN", "Asia/Shanghai",
                "aurora-context.v1", true);
        var oldProvider = choreography.claimGenerationLease(
                fixture.userId, started.turn.id, "pod-a:generation", Duration.ofSeconds(30));

        var persisted = turnMapper.selectById(started.turn.id);
        persisted.leaseExpiresAt = LocalDateTime.ofInstant(
                Instant.now().minusSeconds(1), ZoneOffset.UTC);
        turnMapper.updateById(persisted);
        var takeover = choreography.claimGenerationLease(
                fixture.userId, started.turn.id, "pod-b:generation", Duration.ofSeconds(30));

        assertThat(snapshot.userMessageId()).isEqualTo(fixture.userMessage.id);
        assertThat(snapshot.contextVersion()).isEqualTo("aurora-context.v1");
        assertThat(takeover.fencingToken()).isGreaterThan(oldProvider.fencingToken());
        assertThatThrownBy(() -> choreography.commitPlanFenced(
                fixture.userId, started.turn.id, fixture.reply,
                oldProvider.owner(), oldProvider.fencingToken()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("superseded");

        TurnTimelineVO regenerated = choreography.commitPlanFenced(
                fixture.userId, started.turn.id, fixture.reply,
                takeover.owner(), takeover.fencingToken());
        var immediateDelivery = choreography.claimDeliveryLease(
                fixture.userId, started.turn.id, "pod-b:delivery", Duration.ofSeconds(30));

        assertThat(regenerated.activePlan).isNotNull();
        assertThat(regenerated.bubbles).hasSize(1);
        assertThat(immediateDelivery)
                .as("committed generation must release its lease without timestamp rounding residue")
                .isNotNull();
        assertThat(immediateDelivery.fencingToken()).isGreaterThan(takeover.fencingToken());
        assertThat(regenerated.events).extracting(e -> e.eventType)
                .contains("GENERATION_REQUEST_STAGED", "GENERATION_LEASE_CLAIMED", "PLAN_COMMITTED");
    }

    @Test
    void staleProviderGenerationWithoutPersistedPlanFailsExplicitly() {
        Fixture fixture = fixture(81012L, List.of("provider 尚未安全生成"));
        TurnTimelineVO started = choreography.beginTurn(
                fixture.userId, fixture.session.id, fixture.userMessage.id);

        TurnTimelineVO failed = choreography.failUnrecoverableGeneration(
                fixture.userId, started.turn.id, LocalDateTime.now().plusMinutes(1),
                "PROVIDER_GENERATION_LOST_WITH_RUNTIME");

        assertThat(failed.turn.status).isEqualTo("FAILED");
        assertThat(failed.activePlan).isNull();
        assertThat(failed.bubbles).isEmpty();
        assertThat(failed.events).extracting(e -> e.eventType)
                .contains("GENERATION_DISCARDED", "TURN_FAILED");
    }

    @Test
    void userSafeDeliberationSnapshotsAreMonotonicAndRejectHiddenReasoning() {
        Fixture fixture = fixture(81013L, List.of("speaker 尚未开始"));
        TurnTimelineVO started = choreography.beginTurn(
                fixture.userId, fixture.session.id, fixture.userMessage.id);

        TurnTimelineVO revisionOne = choreography.stageDeliberation(
                fixture.userId, started.turn.id, 0,
                "{\"topicState\":{\"activeTopicId\":\"novel\"},"
                        + "\"responsePlan\":{\"bubblePurposes\":[\"continue-current-thread\"]}}");

        assertThat(revisionOne.deliberations).singleElement()
                .satisfies(snapshot -> {
                    assertThat(snapshot.planRevision).isEqualTo(1);
                    assertThat(snapshot.status).isEqualTo("STAGED");
                    assertThat(snapshot.snapshotJson).contains("continue-current-thread");
                });
        assertThat(revisionOne.events).extracting(e -> e.eventType)
                .contains("DELIBERATION_STAGED");
        assertThatThrownBy(() -> choreography.stageDeliberation(
                fixture.userId, started.turn.id, 0,
                "{\"responsePlan\":{}}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("superseded");
        assertThatThrownBy(() -> choreography.stageDeliberation(
                fixture.userId, started.turn.id, 1,
                "{\"chainOfThought\":\"hidden provider reasoning\"}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("forbidden");
    }

    private Fixture fixture(Long userId, List<String> replies) {
        DialogSession session = new DialogSession();
        session.userId = userId;
        session.title = "choreography test";
        session.sessionType = "AURORA_CHAT";
        session.status = "ACTIVE";
        session.messageCount = 0;
        session.tokenEstimate = 0;
        session.startedAt = LocalDateTime.now();
        sessionMapper.insert(session);

        DialogMessage userMessage = message(session.id, userId, "USER", "我想说一件事");
        messageMapper.insert(userMessage);
        List<DialogMessage> aurora = replies.stream().map(text -> {
            DialogMessage message = message(session.id, userId, "AURORA", text);
            messageMapper.insert(message);
            return message;
        }).toList();

        AuroraReplyVO reply = new AuroraReplyVO();
        reply.messages = replies;
        reply.detectedTheme = "被理解";
        reply.replyTone = "温柔、具体、像朋友";
        reply.aiState = Map.of("provider", "mock", "model", "mock-aurora");
        return new Fixture(userId, session, userMessage, aurora, reply);
    }

    private DialogMessage message(Long sessionId, Long userId, String speaker, String content) {
        DialogMessage message = new DialogMessage();
        message.sessionId = sessionId;
        message.userId = userId;
        message.speaker = speaker;
        message.textContent = content;
        message.inputType = "TEXT";
        message.safetyLevel = "LOW";
        return message;
    }

    private record Fixture(Long userId, DialogSession session, DialogMessage userMessage,
                           List<DialogMessage> auroraMessages, AuroraReplyVO reply) {}
}
