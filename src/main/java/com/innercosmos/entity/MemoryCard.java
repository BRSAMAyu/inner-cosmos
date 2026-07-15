package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("tb_memory_card")
public class MemoryCard extends BaseEntity {
    public Long userId;
    public Long sourceSessionId;
    public String title;
    public String summary;
    public String memoryType;
    public String emotionTags;
    public String keywordTags;
    public String peopleTags;
    public Double intensityScore;
    public Integer recurrenceCount;
    public Double userImportance;
    public Integer triggerCount;
    public Double emotionalGravity;
    public LocalDateTime lastTouchedAt;
    public String visibilityLevel;
    public String status;
    public Integer versionNo;
    public String memoryLayer;
    public Double confidence;
    public String consentScope;
    public Long supersededById;
    public String provenanceRefs;
    public LocalDateTime archivedAt;
    public LocalDateTime forgottenAt;
}
