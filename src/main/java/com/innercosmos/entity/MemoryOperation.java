package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_memory_operation")
public class MemoryOperation extends BaseEntity {
    public Long userId;
    public String operationType;
    public Long primaryMemoryId;
    public String relatedMemoryIds;
    public Integer oldVersion;
    public Integer newVersion;
    public String beforeSnapshot;
    public String afterSnapshot;
    public String evidenceRefs;
    public String modelName;
    public String promptVersion;
    public String reasonCode;
    public Double confidence;
    public String actorType;
    public Long rollbackOfOperationId;
    public String status;
}
