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
    /** Current cross-pod delivery lease owner; diagnostic only, never user-facing. */
    public String leaseOwner;
    /** Monotonic fencing token. Every takeover increments it so an old Pod cannot commit. */
    public Long leaseToken;
    /** UTC wall-time expiry for the short delivery lease. */
    public LocalDateTime leaseExpiresAt;
    public LocalDateTime startedAt;
    public LocalDateTime completedAt;
}
