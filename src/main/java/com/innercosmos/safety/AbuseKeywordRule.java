package com.innercosmos.safety;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AbuseKeywordRule implements SafetyRule {
    private static final List<String> ABUSE_KEYWORDS = List.of(
            "人肉", "威胁", "骚扰", "恐吓", "跟踪", "网暴",
            "侮辱", "诽谤", "泄露隐私", "曝光信息", "公布身份"
    );

    private final List<String> keywords = ABUSE_KEYWORDS;

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
