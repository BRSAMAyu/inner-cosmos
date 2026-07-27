package com.innercosmos.vo;

public class SafetyResult {
    public String riskLevel; // LOW / MEDIUM / HIGH
    public String riskType;
    public String matchedRule;
    public String handledAction;
    public String safeMessage;
    public Boolean blockModelCall;
    public String safetyState; // NORMAL / DISTRESS_WATCH / GENTLE_CHECK_IN / HIGH_CONFIRMED
}
