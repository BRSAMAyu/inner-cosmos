package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_social_group_member")
public class SocialGroupMember extends BaseEntity {
    public Long groupId;
    public Long userId;
    public String memberRole;
    public String status;
}
