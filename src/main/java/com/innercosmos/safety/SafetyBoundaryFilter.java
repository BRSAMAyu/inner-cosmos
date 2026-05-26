package com.innercosmos.safety;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SafetyBoundaryFilter {
    private final List<SafetyRule> rules;

    public SafetyBoundaryFilter(List<SafetyRule> rules) {
        this.rules = rules;
    }

    public SafetyMatch inspect(String text) {
        for (SafetyRule rule : rules) {
            SafetyMatch match = rule.match(text);
            if (match.matched) {
                return match;
            }
        }
        return SafetyMatch.safe();
    }
}
