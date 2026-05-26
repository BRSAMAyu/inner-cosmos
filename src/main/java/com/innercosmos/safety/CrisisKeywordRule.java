package com.innercosmos.safety;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CrisisKeywordRule implements SafetyRule {
    private static final List<String> CRISIS_KEYWORDS = List.of(
            "自杀", "轻生", "杀人", "跳楼", "割腕", "服药自杀",
            "不想活", "寻死", "自残", "了结自己", "结束生命",
            "死了一了百了", "活着没意义", "想死", "去死"
    );

    private final List<String> keywords = CRISIS_KEYWORDS;

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
