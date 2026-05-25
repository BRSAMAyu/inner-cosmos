package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("tb_slow_letter")
public class SlowLetter extends BaseEntity {
    public Long senderUserId;
    public Long receiverUserId;
    public Long receiverCapsuleId;
    public String title;
    public String letterBody;
    public String status;
    public Integer parallaxDistance;
    public LocalDateTime estimatedArrivalAt;
    public LocalDateTime sentAt;
    public LocalDateTime deliveredAt;
    public LocalDateTime readAt;
    public LocalDateTime repliedAt;
}
