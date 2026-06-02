package com.innercosmos.safety;

public class SafetyMatch {
    public boolean matched;
    public String riskType;
    public String riskLevel;
    public String matchedRule;
    public String handledAction;
    public boolean llmReview; // Whether LLM re-check was performed
    public String llmReviewResult; // Result of LLM re-check if performed

    public static SafetyMatch safe() {
        SafetyMatch match = new SafetyMatch();
        match.matched = false;
        match.llmReview = false;
        return match;
    }

    public static SafetyMatch hit(String riskType, String riskLevel, String matchedRule, String handledAction) {
        SafetyMatch match = new SafetyMatch();
        match.matched = true;
        match.riskType = riskType;
        match.riskLevel = riskLevel;
        match.matchedRule = matchedRule;
        match.handledAction = handledAction;
        match.llmReview = false;
        return match;
    }

    public static SafetyMatch hitWithLlmReview(String riskType, String riskLevel, String matchedRule, String handledAction, String llmReviewResult) {
        SafetyMatch match = hit(riskType, riskLevel, matchedRule, handledAction);
        match.llmReview = true;
        match.llmReviewResult = llmReviewResult;
        return match;
    }
}
