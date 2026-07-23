package com.innercosmos.safety;

import com.innercosmos.util.SafetyTextNormalizer;
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
        // Gemini audit 3.7 (CONFIRMED/P0): see CrisisKeywordRule for why this normalizes first.
        String normalized = SafetyTextNormalizer.normalizeForMatch(text);
        for (String keyword : keywords) {
            if (normalized.contains(keyword)) {
                return SafetyMatch.hit("ABUSE", "HIGH", keyword, "BLOCKED");
            }
        }
        return SafetyMatch.safe();
    }
}
