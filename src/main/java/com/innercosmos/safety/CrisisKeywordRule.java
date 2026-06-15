package com.innercosmos.safety;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CrisisKeywordRule implements SafetyRule {
    // Synchronous BLOCK list: NEAR-ALWAYS-CRISIS direct terms only.
    // Ordinary / literary / casual vocabulary that can appear in non-crisis sentences
    // (活着好累, 绝望, 痛苦, 结束, 了结, 受够, 没有意义, 想消失, 撑不住, 熬不下去,
    //  活着没意义, 想离开) is intentionally ABSENT here — those belong in the semantic
    // DistressSignalDetector tier (vision §9/§13: do NOT medicalize casual venting).
    private static final List<String> CRISIS_KEYWORDS = List.of(
            "自杀", "轻生", "杀人", "跳楼", "割腕", "服药自杀",
            "不想活", "寻死", "自残", "了结自己", "结束生命",
            "死了一了百了", "想死", "去死",
            // Near-always-crisis direct terms (gap analysis)
            "我希望我从没出生", "生不如死", "活不下去",
            "不想在了", "希望消失"
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
