package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_dialog_summary")
public class DialogSummary extends BaseEntity {
    public Long sessionId;
    public Long userId;
    public String summaryText;
    public String keyTopics;
    public String emotionTone;
    public Integer messageCountAtSummary;
}
