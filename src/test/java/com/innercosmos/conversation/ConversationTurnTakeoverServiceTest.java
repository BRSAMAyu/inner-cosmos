package com.innercosmos.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.innercosmos.conversation.entity.ConversationTurn;
import com.innercosmos.conversation.entity.MessageBubble;
import com.innercosmos.conversation.service.ConversationChoreographyService;
import com.innercosmos.conversation.service.ConversationTurnTakeoverService;
import com.innercosmos.conversation.vo.TurnTimelineVO;
import com.innercosmos.service.AuroraAgentService;
import com.innercosmos.service.DialogService;
import com.innercosmos.vo.AuroraReplyVO;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConversationTurnTakeoverServiceTest {

    @Test
    void persistedPlanResumesOnlyPendingBubblesAndCompletesUnderOneDeliveryFence() {
        ConversationChoreographyService choreography = mock(ConversationChoreographyService.class);
        DialogService dialogs = mock(DialogService.class);
        AuroraAgentService aurora = mock(AuroraAgentService.class);
        ConversationTurnTakeoverService takeover = new ConversationTurnTakeoverService(
                choreography, dialogs, aurora, Duration.ofSeconds(15), "pod-b");

        MessageBubble committed = bubble(1, "already committed", "COMMITTED");
        MessageBubble pending = bubble(2, "continue from here", "PLANNED");
        TurnTimelineVO planned = timeline(
                41L, 7L, 13L, 99L, "PARTIAL", List.of(committed, pending));
        TurnTimelineVO completed = timeline(
                41L, 7L, 13L, 99L, "COMPLETED", List.of(committed, pending));
        var lease = new ConversationChoreographyService.DeliveryLease(
                "pod-b:takeover:delivery", 3L, LocalDateTime.now().plusSeconds(15));

        when(choreography.timeline(7L, 41L)).thenReturn(planned);
        when(choreography.claimDeliveryLease(eq(7L), eq(41L), any(), any()))
                .thenReturn(lease);
        when(choreography.completeTurnFenced(
                7L, 41L, lease.owner(), lease.fencingToken()))
                .thenReturn(completed);

        TurnTimelineVO result = takeover.resumeOrFail(
                7L, 41L, LocalDateTime.now(), "runtime lost");

        assertThat(result.turn.status).isEqualTo("COMPLETED");
        verify(choreography, times(1)).deliverBubbleFenced(
                eq(7L), eq(41L), eq(2),
                eq(lease.owner()), eq(lease.fencingToken()), any(), any());
        verifyNoInteractions(aurora);
    }

    @Test
    void noPlanYetRegeneratesExistingTurnThenUsesTheNormalFencedDeliveryPath() {
        ConversationChoreographyService choreography = mock(ConversationChoreographyService.class);
        DialogService dialogs = mock(DialogService.class);
        AuroraAgentService aurora = mock(AuroraAgentService.class);
        ConversationTurnTakeoverService takeover = new ConversationTurnTakeoverService(
                choreography, dialogs, aurora, Duration.ofSeconds(15), "pod-b");

        TurnTimelineVO generating = timeline(41L, 7L, 13L, null, "GENERATING", List.of());
        MessageBubble bubble = new MessageBubble();
        bubble.bubbleOrder = 1;
        bubble.content = "regenerated answer";
        bubble.status = "PLANNED";
        TurnTimelineVO planned = timeline(41L, 7L, 13L, 99L, "PLANNED", List.of(bubble));
        TurnTimelineVO completed = timeline(41L, 7L, 13L, 99L, "COMPLETED", List.of(bubble));
        var snapshot = new ConversationChoreographyService.GenerationRequestSnapshot(
                41L, 13L, 77L, "DAILY_TALK", "en-US", "US",
                "America/Los_Angeles", "aurora-context.v1", true);
        var generationLease = new ConversationChoreographyService.DeliveryLease(
                "pod-b:takeover:generation", 2L, LocalDateTime.now().plusMinutes(2));
        var deliveryLease = new ConversationChoreographyService.DeliveryLease(
                "pod-b:takeover:delivery", 3L, LocalDateTime.now().plusSeconds(15));

        when(choreography.timeline(7L, 41L)).thenReturn(generating, planned);
        when(choreography.generationRequest(7L, 41L)).thenReturn(snapshot);
        when(choreography.claimGenerationLease(eq(7L), eq(41L), any(), any()))
                .thenReturn(generationLease);
        when(aurora.resumeExistingTurn(
                7L, snapshot, generationLease.owner(), generationLease.fencingToken()))
                .thenReturn(new AuroraReplyVO());
        when(choreography.claimDeliveryLease(eq(7L), eq(41L), any(), any()))
                .thenReturn(deliveryLease);
        when(choreography.completeTurnFenced(
                7L, 41L, deliveryLease.owner(), deliveryLease.fencingToken()))
                .thenReturn(completed);

        TurnTimelineVO result = takeover.resumeOrFail(
                7L, 41L, LocalDateTime.now(), "runtime lost");

        assertThat(result.turn.status).isEqualTo("COMPLETED");
        verify(aurora).resumeExistingTurn(
                7L, snapshot, generationLease.owner(), generationLease.fencingToken());
        verify(choreography, never()).failUnrecoverableGeneration(any(), any(), any(), any());
        verify(choreography).deliverBubbleFenced(
                eq(7L), eq(41L), eq(1),
                eq(deliveryLease.owner()), eq(deliveryLease.fencingToken()), any(), any());
    }

    @Test
    void preMigrationGenerationWithoutRecoveryEnvelopeFailsExplicitly() {
        ConversationChoreographyService choreography = mock(ConversationChoreographyService.class);
        DialogService dialogs = mock(DialogService.class);
        AuroraAgentService aurora = mock(AuroraAgentService.class);
        ConversationTurnTakeoverService takeover = new ConversationTurnTakeoverService(
                choreography, dialogs, aurora, Duration.ofSeconds(15), "pod-b");
        TurnTimelineVO generating = timeline(41L, 7L, 13L, null, "GENERATING", List.of());
        TurnTimelineVO failed = timeline(41L, 7L, 13L, null, "FAILED", List.of());
        LocalDateTime cutoff = LocalDateTime.now();

        when(choreography.timeline(7L, 41L)).thenReturn(generating);
        when(choreography.generationRequest(7L, 41L)).thenReturn(null);
        when(choreography.failUnrecoverableGeneration(
                7L, 41L, cutoff, "runtime lost")).thenReturn(failed);

        TurnTimelineVO result = takeover.resumeOrFail(
                7L, 41L, cutoff, "runtime lost");

        assertThat(result.turn.status).isEqualTo("FAILED");
        verify(choreography, never()).claimGenerationLease(any(), any(), any(), any());
        verifyNoInteractions(aurora);
    }

    @Test
    void terminalTurnIsAnIdempotentNoOp() {
        ConversationChoreographyService choreography = mock(ConversationChoreographyService.class);
        DialogService dialogs = mock(DialogService.class);
        AuroraAgentService aurora = mock(AuroraAgentService.class);
        ConversationTurnTakeoverService takeover = new ConversationTurnTakeoverService(
                choreography, dialogs, aurora, Duration.ofSeconds(15), "pod-b");
        TurnTimelineVO completed = timeline(
                41L, 7L, 13L, 99L, "COMPLETED", List.of());
        when(choreography.timeline(7L, 41L)).thenReturn(completed);

        TurnTimelineVO result = takeover.resumeOrFail(
                7L, 41L, LocalDateTime.now(), "runtime lost");

        assertThat(result).isSameAs(completed);
        verify(choreography, never()).claimDeliveryLease(any(), any(), any(), any());
        verifyNoInteractions(dialogs, aurora);
    }

    private MessageBubble bubble(int order, String content, String status) {
        MessageBubble bubble = new MessageBubble();
        bubble.bubbleOrder = order;
        bubble.content = content;
        bubble.status = status;
        return bubble;
    }

    private TurnTimelineVO timeline(Long turnId, Long userId, Long sessionId,
                                    Long planId, String status, List<MessageBubble> bubbles) {
        TurnTimelineVO timeline = new TurnTimelineVO();
        timeline.turn = new ConversationTurn();
        timeline.turn.id = turnId;
        timeline.turn.userId = userId;
        timeline.turn.sessionId = sessionId;
        timeline.turn.activePlanId = planId;
        timeline.turn.status = status;
        timeline.bubbles = bubbles;
        return timeline;
    }
}
