package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("tb_live_chat_session")
public class LiveChatSession extends BaseEntity {
    public Long inviteId;
    public Long participantOneId;
    public Long participantTwoId;
    public Integer durationMinutes;
    public String status;
    public LocalDateTime startedAt;
    public LocalDateTime endsAt;
    public LocalDateTime endedAt;
    public Long endedByUserId;
}
