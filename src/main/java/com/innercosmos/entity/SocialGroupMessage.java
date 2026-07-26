package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_social_group_message")
public class SocialGroupMessage extends BaseEntity {
    public Long groupId;
    public Long senderUserId;
    public String messageBody;
}
