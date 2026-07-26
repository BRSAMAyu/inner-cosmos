package com.innercosmos.ai.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.conversation.entity.ConversationTurn;
import com.innercosmos.conversation.entity.TurnPlan;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.UserProfile;
import com.innercosmos.entity.WakeIntent;
import com.innercosmos.mapper.ConversationTurnMapper;
import com.innercosmos.mapper.DialogMessageMapper;
import com.innercosmos.mapper.TurnPlanMapper;
import com.innercosmos.service.MemoryLifecycleService;
import com.innercosmos.service.UserService;
import com.innercosmos.service.WakeIntentService;
import com.innercosmos.vo.MemoryOperationResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuroraNaturalActionServiceTest {
    private final TurnPlanMapper plans = mock(TurnPlanMapper.class);
    private final ConversationTurnMapper turns = mock(ConversationTurnMapper.class);
    private final DialogMessageMapper messages = mock(DialogMessageMapper.class);
    private final MemoryLifecycleService memories = mock(MemoryLifecycleService.class);
    private final WakeIntentService wakeIntents = mock(WakeIntentService.class);
    private final UserService users = mock(UserService.class);
    private final ObjectMapper json = new ObjectMapper();
    private AuroraNaturalActionService service;

    @BeforeEach
    void setUp() {
        service = new AuroraNaturalActionService(new AuroraNaturalActionParser(), plans, turns,
                messages, memories, wakeIntents, users, json);
        UserProfile profile = new UserProfile();
        profile.timezone = "Asia/Singapore";
        lenient().when(users.getProfile(7L)).thenReturn(profile);
    }

    @Test
    void proposalDoesNotWriteAnythingBeforeConfirmation() {
        currentTurn(20L);

        var reply = service.intercept(7L, 9L, 20L, "Remember that I prefer direct questions.");

        assertThat(reply.proposedActionType).isEqualTo("REMEMBER");
        assertThat(reply.proposedActionStatus).isEqualTo("PENDING_CONFIRMATION");
        assertThat(reply.messages.getFirst()).contains("reply “Confirm”").contains("Nothing changes before confirmation");
        verifyNoInteractions(memories, wakeIntents);
        verify(users, never()).updateProfile(anyLong(), any());
    }

    @Test
    void explicitAdjacentConfirmationCreatesPrivateAuditedMemoryOnce() throws Exception {
        currentTurn(21L);
        TurnPlan pending = pending(10L, AuroraNaturalActionParser.REMEMBER,
                json.writeValueAsString(java.util.Map.of("title", "展示备用流程", "content", "网络不稳时切本地流程")),
                "把这件事保存为仅你可见、可追溯的记忆");
        immediatePending(pending, 20L, 21L);
        MemoryCard card = new MemoryCard();
        card.id = 88L;
        when(memories.execute(eq(7L), any())).thenReturn(
                new MemoryOperationResultVO(null, List.of(card), List.of()));

        var reply = service.intercept(7L, 9L, 21L, "确认");

        verify(memories).execute(eq(7L), argThat(command ->
                "ADD".equals(command.operationType())
                        && "网络不稳时切本地流程".equals(command.summary())
                        && command.evidenceRefs().equals("turn-plan:10")));
        assertThat(pending.actionStatus).isEqualTo("EXECUTED");
        assertThat(pending.actionResultRef).isEqualTo("memory:88");
        assertThat(reply.messages.getFirst()).contains("仅你可见").contains("没有自动公开");
    }

    @Test
    void explicitAdjacentConfirmationSchedulesDurableWakeIntentInProfileTimezone() throws Exception {
        currentTurn(31L);
        TurnPlan pending = pending(11L, AuroraNaturalActionParser.REMINDER,
                json.writeValueAsString(java.util.Map.of(
                        "when", "in 2 hours", "purpose", "check the build",
                        "content", "check the build", "timezone", "Asia/Singapore")),
                "Schedule a real reminder");
        immediatePending(pending, 30L, 31L);
        WakeIntent wake = new WakeIntent();
        wake.id = 55L;
        wake.userId = 7L;
        wake.purpose = "check the build";
        wake.reasonForUser = "confirmed";
        wake.content = "check the build";
        wake.timezone = "Asia/Singapore";
        wake.earliestAt = LocalDateTime.of(2026, 7, 26, 8, 50);
        wake.preferredAt = LocalDateTime.of(2026, 7, 26, 9, 0);
        wake.latestAt = LocalDateTime.of(2026, 7, 26, 12, 0);
        wake.status = "PLANNED";
        wake.decisionPolicyVersion = WakeIntentService.POLICY_VERSION;
        when(wakeIntents.scheduleNatural(anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(wake);

        var reply = service.intercept(7L, 9L, 31L, "Confirm");

        verify(wakeIntents).scheduleNatural(7L, "in 2 hours", "check the build",
                "You explicitly confirmed this reminder in Aurora.",
                "check the build", "Asia/Singapore", 9L);
        assertThat(pending.actionStatus).isEqualTo("EXECUTED");
        assertThat(pending.actionResultRef).isEqualTo("wake-intent:55");
        assertThat(reply.messages.getFirst()).contains("Jul 26 at 5:00 PM").doesNotContain("T17:00");
    }

    @Test
    void settingConfirmationOnlyPatchesTheAllowListedField() throws Exception {
        currentTurn(41L);
        TurnPlan pending = pending(12L, AuroraNaturalActionParser.PROFILE_SETTING,
                json.writeValueAsString(java.util.Map.of(
                        "setting", "allowMemoryRecall", "value", "false", "label", "记忆回顾")),
                "调整 Aurora 的可授权设置");
        immediatePending(pending, 40L, 41L);

        service.intercept(7L, 9L, 41L, "确认设置");

        ArgumentCaptor<com.innercosmos.vo.UserProfileVO> patch =
                ArgumentCaptor.forClass(com.innercosmos.vo.UserProfileVO.class);
        verify(users).updateProfile(eq(7L), patch.capture());
        assertThat(patch.getValue().allowMemoryRecall).isFalse();
        assertThat(patch.getValue().weatherAwarenessEnabled).isNull();
        assertThat(pending.actionStatus).isEqualTo("EXECUTED");
    }

    @Test
    void unrelatedNextTurnExpiresProposalSoLaterConfirmationCannotExecuteIt() throws Exception {
        currentTurn(51L);
        TurnPlan pending = pending(13L, AuroraNaturalActionParser.REMEMBER,
                json.writeValueAsString(java.util.Map.of("title", "旧提案", "content", "不应被保存")),
                "旧提案");
        immediatePending(pending, 50L, 51L);

        var reply = service.intercept(7L, 9L, 51L, "我们先聊点别的");

        assertThat(reply).isNull();
        assertThat(pending.actionStatus).isEqualTo("EXPIRED");
        verifyNoInteractions(memories, wakeIntents);
    }

    private void currentTurn(long id) {
        ConversationTurn current = turn(id, 9L);
        when(turns.selectById(id)).thenReturn(current);
        when(plans.selectList(any())).thenReturn(List.of());
    }

    private void immediatePending(TurnPlan pending, long pendingTurnId, long currentTurnId) {
        ConversationTurn source = turn(pendingTurnId, 9L);
        when(plans.selectList(any())).thenReturn(List.of(pending));
        when(plans.selectOne(any())).thenReturn(pending);
        when(turns.selectById(pendingTurnId)).thenReturn(source);
        when(turns.selectById(currentTurnId)).thenReturn(turn(currentTurnId, 9L));
        when(turns.selectCount(any())).thenReturn(0L);
    }

    private TurnPlan pending(long id, String type, String payload, String summary) {
        TurnPlan plan = new TurnPlan();
        plan.id = id;
        plan.turnId = id == 10 ? 20L : id == 11 ? 30L : id == 12 ? 40L : 50L;
        plan.userId = 7L;
        plan.proposedActionType = type;
        plan.proposedActionPayload = payload;
        plan.proposedActionSummary = summary;
        plan.actionStatus = "PENDING_CONFIRMATION";
        return plan;
    }

    private ConversationTurn turn(long id, long sessionId) {
        ConversationTurn turn = new ConversationTurn();
        turn.id = id;
        turn.userId = 7L;
        turn.sessionId = sessionId;
        turn.userMessageId = id + 100;
        return turn;
    }
}
