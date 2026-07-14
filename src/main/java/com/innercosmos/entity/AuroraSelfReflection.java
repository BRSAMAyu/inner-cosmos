package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_aurora_self_reflection")
public class AuroraSelfReflection extends BaseEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long userId;
    @TableField("trigger_type")
    public String trigger;
    public String depth;
    public String summary;
    public Long relatedStatementId;
    public String dimension;
    public String proposedBelief;
    public Double confidence;
    public String status;
    public String riskFlags;
    public String evidenceRefs;  // JSON array of evidence ids
}
