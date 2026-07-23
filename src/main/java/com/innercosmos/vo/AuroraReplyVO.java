package com.innercosmos.vo;

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
    /**
     * Aurora's composed "inner voice" (心声) text for this turn, or {@code null} when none was
     * composed (single-pass runtime, user disabled it, or composition failed/was skipped). Only
     * text lives here -- synthesized audio is produced (and the {@code inner_voice} SSE event
     * emitted) only on the streaming path; see {@code AuroraAgentServiceImpl.stream}.
     */
    public String innerVoiceText;
}
