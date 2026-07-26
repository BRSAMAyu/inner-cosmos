package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_live_chat_message")
public class LiveChatMessage extends BaseEntity {
    public Long sessionId;
    public Long senderUserId;
    public String messageBody;
}
