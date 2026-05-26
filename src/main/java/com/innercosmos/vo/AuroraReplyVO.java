package com.innercosmos.vo;

import java.util.List;

public class AuroraReplyVO {
    public List<String> messages;
    public String replyTone;
    public String detectedTheme;
    public String nextQuestion;
    public Boolean suggestSettle;
    public Boolean memoryReferenced;
    public List<Long> referencedMemoryIds;
}
