package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("tb_agent_user_relationship")
public class AgentUserRelationship extends BaseEntity {
    public Long userId;
    public String relationshipStage;
    public Integer intimacyLevel;
    public Integer trustLevel;
    public Integer familiarityLevel;
    public Integer userDisclosureLevel;
    public String auroraRoleInUserLife;
    public String sharedHistoryRefs;
    public String interactionRituals;
    public String preferredAddressing;
    public String relationshipBoundaries;
    public String continuityAnchors;
    public LocalDateTime lastStageChangeAt;
    public LocalDateTime lastUpdatedAt;

    public String toPromptString() {
        return String.format("stage=%s intimacy=%d trust=%d familiarity=%d role=%s addressing=%s",
                relationshipStage, intimacyLevel, trustLevel, familiarityLevel,
                auroraRoleInUserLife, preferredAddressing);
    }
}