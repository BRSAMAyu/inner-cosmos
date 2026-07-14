package com.innercosmos.conversation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.innercosmos.entity.BaseEntity;
import java.time.LocalDateTime;

@TableName("tb_message_bubble")
public class MessageBubble extends BaseEntity {
    public Long turnId;
    public Long planId;
    public Long userId;
    public Long dialogMessageId;
    public Integer bubbleOrder;
    public String purpose;
    public String content;
    public String status;
    public Integer sendAfterMs;
    public Integer deliveredChars;
    public Boolean requiresNoInterruption;
    public LocalDateTime plannedAt;
    public LocalDateTime sentAt;
    public LocalDateTime cancelledAt;
}
