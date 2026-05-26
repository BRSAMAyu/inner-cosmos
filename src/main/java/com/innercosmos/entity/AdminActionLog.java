package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_admin_action_log")
public class AdminActionLog extends BaseEntity {
    public Long adminUserId;
    public String actionType;
    public String targetType;
    public Long targetId;
    public String detail;
}
