package com.innercosmos.conversation.service;

import com.innercosmos.conversation.entity.MessageBubble;
import com.innercosmos.conversation.vo.TurnTimelineVO;
import com.innercosmos.service.DialogService;
import com.innercosmos.service.AuroraAgentService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Recovers a turn at the only two safe boundaries: regenerate before any assistant plan/content
 * exists, or resume delivery from an already-persisted plan. Database leases and fencing tokens
 * make both paths single-authority across API Pods.
 */
@Service
public class ConversationTurnTakeoverService {
    private final ConversationChoreographyService choreography;
    private final DialogService dialogService;
    private final AuroraAgentService auroraAgentService;
    private final Duration leaseTtl;
    private final String instanceId;
    @Value("${inner-cosmos.aurora.turn-recovery.generation-lease-ttl:PT2M}")
    private Duration generationLeaseTtl = Duration.ofMinutes(2);

    public ConversationTurnTakeoverService(
            ConversationChoreographyService choreography,
            DialogService dialogService,
            AuroraAgentService auroraAgentService,
            @Value("${inner-cosmos.aurora.turn-recovery.lease-ttl:PT15S}") Duration leaseTtl,
            @Value("${inner-cosmos.instance-id:${HOSTNAME:local}}") String instanceId) {
        this.choreography = choreography;
        this.dialogService = dialogService;
        this.auroraAgentService = auroraAgentService;
        this.leaseTtl = leaseTtl == null || leaseTtl.isNegative() || leaseTtl.isZero()
                ? Duration.ofSeconds(15) : leaseTtl;
        this.instanceId = instanceId == null || instanceId.isBlank() ? "local" : instanceId;
    }

    public TurnTimelineVO resumeOrFail(Long userId, Long turnId,
                                       LocalDateTime generationCutoff, String failureReason) {
        TurnTimelineVO current = choreography.timeline(userId, turnId);
        if (terminal(current.turn.status)) return current;
        String owner = instanceId + ":takeover:" + UUID.randomUUID();
        if (current.turn.activePlanId == null) {
            ConversationChoreographyService.GenerationRequestSnapshot snapshot =
                    choreography.generationRequest(userId, turnId);
            if (snapshot == null) {
                // Legacy in-flight turns created before V33 have no safe reconstruction envelope.
                return choreography.failUnrecoverableGeneration(
                        userId, turnId, generationCutoff, failureReason);
            }
            ConversationChoreographyService.DeliveryLease generationLease =
                    choreography.claimGenerationLease(
                            userId, turnId, owner, generationLeaseTtl);
            if (generationLease == null) return choreography.timeline(userId, turnId);
            auroraAgentService.resumeExistingTurn(
                    userId, snapshot, generationLease.owner(), generationLease.fencingToken());
            current = choreography.timeline(userId, turnId);
            if (terminal(current.turn.status) || current.turn.activePlanId == null) return current;
        }

        ConversationChoreographyService.DeliveryLease lease =
                choreography.claimDeliveryLease(userId, turnId, owner, leaseTtl);
        if (lease == null) return choreography.timeline(userId, turnId);
        Long sessionId = current.turn.sessionId;

        for (MessageBubble bubble : current.bubbles) {
            if (!"PLANNED".equals(bubble.status)) continue;
            choreography.deliverBubbleFenced(
                    userId, turnId, bubble.bubbleOrder,
                    lease.owner(), lease.fencingToken(), leaseTtl,
                    () -> dialogService.saveAuroraMessage(
                            userId, sessionId, bubble.content));
        }
        return choreography.completeTurnFenced(
                userId, turnId, lease.owner(), lease.fencingToken());
    }

    private boolean terminal(String status) {
        return "COMPLETED".equals(status) || "CANCELLED".equals(status)
                || "INTERRUPTED".equals(status) || "FAILED".equals(status);
    }
}
