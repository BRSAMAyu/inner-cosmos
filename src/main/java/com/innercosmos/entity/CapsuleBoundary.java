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
    /** Monotonic compare-and-set token exposed as the HTTP ETag. */
    public Integer version;
}
