package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_memory_projection_receipt")
public class MemoryProjectionReceipt extends BaseEntity {
    public Long userId;
    public Long operationId;
    public String projectionType;
    public String status;
    public Integer generation;
    public String detail;
}
