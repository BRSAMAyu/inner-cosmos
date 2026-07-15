package com.innercosmos.skill;

import java.util.List;
import java.util.Map;

public class PsychologySkillManifest {
    public String id;
    public String version;
    public String owner;
    public Map<String, String> title;
    public Map<String, String> description;
    public Integer estimatedMinutes;
    public String riskTier;
    public String agentInvocation;
    public String userInvocation;
    public List<String> requiredScopes;
    public List<String> allowedData;
    public List<String> allowedTools;
    public List<String> requiredInputs;
    public List<String> evidence;
    public Map<String, String> limitations;
    public List<String> retentionChoices;
    public String evaluationSuite;
    public String fallback;
    public String escalation;
}
