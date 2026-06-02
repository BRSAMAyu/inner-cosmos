package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("tb_relation_mention")
public class RelationMention extends BaseEntity {
    public Long userId;
    public Long sourceSessionId;
    public Long memoryCardId;
    public String relationLabel;
    public String relationType;
    public String emotionTags;
    public String triggerSummary;
    public String boundaryHint;

    /**
     * Timeline point for relationship visualization.
     */
    public static class TimelinePoint {
        public LocalDateTime timestamp;
        public String emotions;
        public String summary;

        public TimelinePoint(LocalDateTime timestamp, String emotions, String summary) {
            this.timestamp = timestamp;
            this.emotions = emotions;
            this.summary = summary;
        }
    }
}
