package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_social_group")
public class SocialGroup extends BaseEntity {
    public Long ownerUserId;
    public String groupName;
    public String intro;
    public String visibility;
}
