package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_capsule_boundary")
public class CapsuleBoundary extends BaseEntity {
    public Long capsuleId;
    public String allowTopics;
    public String blockedTopics;
    public Integer maxConversationTurns;
    public Boolean allowLetterRequest;
    public String privacyLevel;
}
