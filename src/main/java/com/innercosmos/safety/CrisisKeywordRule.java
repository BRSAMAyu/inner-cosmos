package com.innercosmos.safety;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CrisisKeywordRule implements SafetyRule {
    private final List<String> keywords = List.of("自杀", "轻生", "杀人");

    public SafetyMatch match(String text) {
        if (text == null) {
            return SafetyMatch.safe();
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return SafetyMatch.hit("CRISIS_KEYWORD", "HIGH", keyword, "RESOURCE_PAGE");
            }
        }
        return SafetyMatch.safe();
    }
}
