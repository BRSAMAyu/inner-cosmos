package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("tb_relationship_event")
public class RelationshipEvent extends BaseEntity {
    public Long userId;
    public String eventType;
    public String evidenceTurnIds;
    public String deltaProposed;
    public LocalDateTime appliedAt;
}