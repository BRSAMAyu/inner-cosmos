package com.innercosmos.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.innercosmos.ai.runtime.AuroraDualKernelRuntime;

import java.util.Map;
import java.util.List;

public class AuroraReplyVO {
    /** Durable choreography identity (INNO-CONV-001). */
    public Long turnId;
    public Long planId;
    public Boolean cancelled;
    public List<String> messages;
    public String replyTone;
    public String detectedTheme;
    public String nextQuestion;
    public String smallStep;
    public String featureSuggestion;
    public String featureTarget;
    public Map<String, Object> agentLoop;
    public Map<String, Object> aiState;
    public List<String> riskFlags;
    public Boolean suggestSettle;
    public Boolean memoryReferenced;
    public List<Long> referencedMemoryIds;
    public AuroraMemoryContextVO memoryContext;
    /** Confirmation-gated action metadata. Only the type/summary/status are client-visible. */
    public String proposedActionType;
    public String proposedActionSummary;
    public String proposedActionStatus;
    /** Owner-private execution payload copied into the durable TurnPlan, never returned to clients. */
    @JsonIgnore
    public String proposedActionPayloadJson;
    /** In-process only: consumed after {@code turn.completed}; never part of the public API. */
    @JsonIgnore
    public AuroraDualKernelRuntime.InnerVoiceRequest innerVoiceRequest;
    /** Background planner result consumed only after the visible turn has completed. */
    @JsonIgnore
    public java.util.concurrent.CompletableFuture<AuroraDualKernelRuntime.InnerVoiceRequest> deferredInnerVoiceRequest;
    /** In-process truthful lifecycle evidence for the background planner. */
    @JsonIgnore
    public java.util.concurrent.CompletableFuture<AuroraDualKernelRuntime.PlannerRunEvidence> backgroundPlannerEvidence;
}
