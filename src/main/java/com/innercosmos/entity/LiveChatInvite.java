package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("tb_live_chat_invite")
public class LiveChatInvite extends BaseEntity {
    public Long inviterUserId;
    public Long inviteeUserId;
    public Integer durationMinutes;
    public String status;
    public LocalDateTime expiresAt;
    public LocalDateTime respondedAt;
}
