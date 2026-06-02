package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_safety_event")
public class SafetyEvent extends BaseEntity {
    public Long userId;
    public Long sessionId;
    public Long messageId;
    public String riskType;
    public String riskLevel;
    public String matchedRule;
    public String handledAction;
    public String triggerScene;
}
