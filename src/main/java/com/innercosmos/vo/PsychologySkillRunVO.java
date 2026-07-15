package com.innercosmos.vo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class PsychologySkillRunVO {
    public Long id;
    public String skillId;
    public String skillVersion;
    public String locale;
    public String status;
    public String riskTier;
    public String retentionChoice;
    public List<String> consentScopes;
    public Map<String, Object> result;
    public List<String> evidence;
    public String escalationCode;
    public LocalDateTime createdAt;
    public LocalDateTime revokedAt;
}
