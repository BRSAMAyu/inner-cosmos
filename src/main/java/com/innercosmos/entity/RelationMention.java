package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_relation_mention")
public class RelationMention extends BaseEntity {
    public Long userId;
    public Long sourceSessionId;
    public Long memoryCardId;
    public String relationLabel;
    public String relationType;
    public String emotionTags;
    public String triggerSummary;
    public String boundaryHint;
}
