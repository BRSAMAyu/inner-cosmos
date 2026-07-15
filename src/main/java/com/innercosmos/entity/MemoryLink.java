package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_memory_link")
public class MemoryLink extends BaseEntity {
    public Long userId;
    public Long sourceMemoryId;
    public Long targetMemoryId;
    public String linkType;
    public Double strength;
    public String evidenceRefs;
    public String status;
}
