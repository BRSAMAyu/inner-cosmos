package com.innercosmos.vo;

import java.util.Map;
import java.util.List;

public class AuroraReplyVO {
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
}
