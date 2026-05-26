package com.innercosmos.safety;

public class SafetyMatch {
    public boolean matched;
    public String riskType;
    public String riskLevel;
    public String matchedRule;
    public String handledAction;

    public static SafetyMatch safe() {
        SafetyMatch match = new SafetyMatch();
        match.matched = false;
        return match;
    }

    public static SafetyMatch hit(String riskType, String riskLevel, String matchedRule, String handledAction) {
        SafetyMatch match = new SafetyMatch();
        match.matched = true;
        match.riskType = riskType;
        match.riskLevel = riskLevel;
        match.matchedRule = matchedRule;
        match.handledAction = handledAction;
        return match;
    }
}
