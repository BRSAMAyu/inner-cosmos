package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("tb_session_summary")
public class SessionSummary extends BaseEntity {
    public Long userId;
    public Long sessionId;
    public String summary2Sentences;
    public String keyTopics;
    public String emotionalArc;
    public LocalDateTime startedAt;
    public LocalDateTime closedAt;
}