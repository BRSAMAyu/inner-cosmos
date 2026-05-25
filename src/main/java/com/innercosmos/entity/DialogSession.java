package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("tb_dialog_session")
public class DialogSession extends BaseEntity {
    public Long userId;
    public String title;
    public String sessionType;
    public String status;
    public String summaryAnchor;
    public Integer messageCount;
    public Integer tokenEstimate;
    public LocalDateTime startedAt;
    public LocalDateTime endedAt;
}
