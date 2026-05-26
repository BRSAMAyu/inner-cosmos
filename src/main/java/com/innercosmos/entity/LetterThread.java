package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("tb_letter_thread")
public class LetterThread extends BaseEntity {
    public Long firstLetterId;
    public Long participantA;
    public Long participantB;
    public Long capsuleId;
    public String status;
    public LocalDateTime lastLetterAt;
}
