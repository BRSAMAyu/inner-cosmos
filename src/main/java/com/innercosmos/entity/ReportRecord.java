package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_report_record")
public class ReportRecord extends BaseEntity {
    public Long reporterUserId;
    public String targetType;
    public Long targetId;
    public String reason;
    public String status;
}
