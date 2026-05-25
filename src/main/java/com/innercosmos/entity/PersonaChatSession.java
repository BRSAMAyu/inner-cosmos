package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_persona_chat_session")
public class PersonaChatSession extends BaseEntity {
    public Long visitorUserId;
    public Long capsuleId;
    public String status;
    public Integer turnCount;
    public Integer dailyLimit;
}
