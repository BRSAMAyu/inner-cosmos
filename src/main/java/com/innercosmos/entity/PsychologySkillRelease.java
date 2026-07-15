package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("tb_psychology_skill_release")
public class PsychologySkillRelease extends BaseEntity {
    public String skillId;
    public String skillVersion;
    public String manifestHash;
    public String evaluationSuite;
    public String evaluationStatus;
    public String reviewStatus;
    public String reviewNote;
    public Long reviewedByUserId;
    public LocalDateTime reviewedAt;
    public String releaseStatus;
    public Boolean enabled;
    public String disabledReason;
    public LocalDateTime publishedAt;
    public Long supersedesReleaseId;
}
