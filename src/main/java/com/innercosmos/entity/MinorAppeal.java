package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** CP-08 minor-intercept appeal: a restricted user's documented path back to the adult service. */
@TableName("tb_minor_appeal")
public class MinorAppeal extends BaseEntity {
    public Long userId;
    public String statement;
    /** PENDING, ACCEPTED or REJECTED. */
    public String status;
    public Long decidedBy;
    public String decisionNote;
}
