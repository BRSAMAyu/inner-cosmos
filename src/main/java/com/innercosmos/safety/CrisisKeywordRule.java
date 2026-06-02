package com.innercosmos.safety;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CrisisKeywordRule implements SafetyRule {
    private static final List<String> CRISIS_KEYWORDS = List.of(
            "自杀", "轻生", "杀人", "跳楼", "割腕", "服药自杀",
            "不想活", "寻死", "自残", "了结自己", "结束生命",
            "死了一了百了", "活着没意义", "想死", "去死",
            // Additional critical keywords from gap analysis
            "我希望我从没出生", "想消失", "活着好累",
            "活不下去", "熬不下去", "生不如死",
            "希望消失", "想离开", "不想在了",
            "撑不住", "受够", "绝望", "痛苦",
            "结束", "了结", "没有意义"
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
