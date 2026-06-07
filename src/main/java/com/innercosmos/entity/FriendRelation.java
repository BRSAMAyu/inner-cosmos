package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_friend_relation")
public class FriendRelation extends BaseEntity {
    public Long requesterId;
    public Long addresseeId;
    public String status;
    public String source;
}
