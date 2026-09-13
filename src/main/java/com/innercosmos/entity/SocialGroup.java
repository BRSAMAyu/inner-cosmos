package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_social_group")
public class SocialGroup extends BaseEntity {
    /** Lifecycle: ACTIVE, or DISSOLVED once the owner dissolves the group (V50). */
    public String status;
    public Long ownerUserId;
    public String groupName;
    public String intro;
    public String visibility;
}
