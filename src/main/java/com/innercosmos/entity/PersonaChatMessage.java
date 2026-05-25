package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_persona_chat_message")
public class PersonaChatMessage extends BaseEntity {
    public Long sessionId;
    public String senderType;
    public String textContent;
}
