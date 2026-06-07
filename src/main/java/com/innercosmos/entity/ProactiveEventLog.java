package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("tb_proactive_event_log")
public class ProactiveEventLog extends BaseEntity {
    public Long userId;
    public String eventType;
    public String triggerMeta;
    public String content;
    public LocalDateTime sentAt;
    public LocalDateTime userRespondedAt;
    public Boolean accepted;
    public String decisionSource;
    public String reasonInternal;
}