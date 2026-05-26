package com.innercosmos.safety;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AbuseKeywordRule implements SafetyRule {
    private final List<String> keywords = List.of("人肉", "威胁", "骚扰");

    public SafetyMatch match(String text) {
        if (text == null) {
            return SafetyMatch.safe();
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return SafetyMatch.hit("ABUSE", "HIGH", keyword, "BLOCKED");
            }
        }
        return SafetyMatch.safe();
    }
}
