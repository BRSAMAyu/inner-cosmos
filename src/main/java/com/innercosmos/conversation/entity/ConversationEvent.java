package com.innercosmos.conversation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.innercosmos.entity.BaseEntity;

@TableName("tb_conversation_event")
public class ConversationEvent extends BaseEntity {
    public Long turnId;
    public Long planId;
    public Long bubbleId;
    public Long userId;
    public Integer eventSequence;
    public String eventType;
    public String causationId;
    public String payloadJson;
}
