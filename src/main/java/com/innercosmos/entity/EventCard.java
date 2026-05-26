package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("tb_event_card")
public class EventCard extends BaseEntity {
    public Long userId;
    public Long sourceSessionId;
    public Long memoryCardId;
    public String eventTitle;
    public String eventSummary;
    public String eventTimeLabel;
    public String scene;
    public String peopleTags;
    public String emotionTags;
}
