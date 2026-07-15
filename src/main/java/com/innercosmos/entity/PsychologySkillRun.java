package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("tb_psychology_skill_run")
public class PsychologySkillRun extends BaseEntity {
    public Long userId;
    public String skillId;
    public String skillVersion;
    public Long releaseId;
    public String manifestHash;
    public String locale;
    public String status;
    public String riskTier;
    public String retentionChoice;
    public String consentScopes;
    public String inputFingerprint;
    public String resultJson;
    public String evidenceRefs;
    public String escalationCode;
    public LocalDateTime revokedAt;
}
