package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_letter_status_log")
public class LetterStatusLog extends BaseEntity {
    public Long letterId;
    public String fromStatus;
    public String toStatus;
    public Long operatorUserId;
    public String reason;
}
