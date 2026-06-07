package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("tb_private_timer")
public class PrivateTimer extends BaseEntity {
    public Long userId;
    public LocalDateTime fireAt;
    public String kind;
    public String content;
    public LocalDateTime createdAt;
    public LocalDateTime firedAt;
    public LocalDateTime cancelledAt;
}