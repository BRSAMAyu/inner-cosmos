package com.innercosmos.conversation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.innercosmos.entity.BaseEntity;
import java.time.LocalDateTime;

@TableName("tb_conversation_turn")
public class ConversationTurn extends BaseEntity {
    public Long sessionId;
    public Long userId;
    public Long userMessageId;
    public Long activePlanId;
    public String status;
    public Integer nextEventSequence;
    public LocalDateTime startedAt;
    public LocalDateTime completedAt;
}
