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
import com.innercosmos.vo.AuroraReplyVO;
import java.time.LocalDateTime;
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
        assertThat(choreography.latestInterruptionContext(fixture.userId, fixture.session.id))
                .contains("已说出的内容：已经说出的第一条 / 不再发送")
                .contains("原计划但未发送的内容：的第二条 / 不再发送的第三条")
                .contains("不要重复已说内容");
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
