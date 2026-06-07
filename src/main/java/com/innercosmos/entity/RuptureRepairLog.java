package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("tb_rupture_repair_log")
public class RuptureRepairLog extends BaseEntity {
    public Long userId;
    public String event;
    public String userFeedback;
    public String repairAction;
    public String status;
    public LocalDateTime createdAt;
}