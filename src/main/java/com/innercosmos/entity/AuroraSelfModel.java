package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("tb_aurora_self_model")
public class AuroraSelfModel extends BaseEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long userId;
    public String dimension;
    public String belief;
    public Double confidence;
    public String evidenceRefs;
    public String status;
    public LocalDateTime committedAt;
    public Integer revisionCount;
}
