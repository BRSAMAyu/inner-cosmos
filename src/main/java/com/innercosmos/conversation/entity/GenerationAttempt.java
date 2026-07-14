package com.innercosmos.conversation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.innercosmos.entity.BaseEntity;
import java.time.LocalDateTime;

@TableName("tb_generation_attempt")
public class GenerationAttempt extends BaseEntity {
    public Long turnId;
    public Long planId;
    public Long userId;
    public Integer attemptNumber;
    public String status;
    public String provider;
    public String modelName;
    public LocalDateTime startedAt;
    public LocalDateTime completedAt;
}
