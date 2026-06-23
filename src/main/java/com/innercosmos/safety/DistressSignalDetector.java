package com.innercosmos.safety;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * High-recall distress-signal detector.
 *
 * This is a SIGNAL tier, not a crisis block. It fires on language that expresses
 * burden / hopelessness / a wish to vanish or be relieved of existence, but does
 * NOT contain an explicit crisis keyword already caught by {@link CrisisKeywordRule}.
 *
 * When a distress signal is present without an explicit crisis keyword,
 * {@code SafetyServiceImpl} asks {@link SafetyReviewService#recheckSync} to make a
 * synchronous semantic judgment (genuine crisis vs. casual venting) before the
 * response is sent. Casual venting ("今天加班累死了", "活着好累" after a long day) must
 * NOT be blocked or medicalized (vision §9/§13).
 *
 * Phrases here intentionally overlap neither with the crisis keyword list nor with
 * ordinary tired/stressed vocabulary. Keeping this set tight limits false positives.
 */
@Component
public class DistressSignalDetector {

    /**
     * Distress phrases that warrant a semantic re-check when they appear WITHOUT
     * an explicit crisis keyword. Chosen for high recall on implicit-crisis vectors
     * while avoiding plain hyperbole ("累死了", "困死了" are excluded on purpose).
     */
    private static final List<String> DISTRESS_SIGNALS = List.of(
            "累赘", "拖累", "拖累大家", "我是累赘",
            "负担", "我是负担", "是个负担",
            "没意义", "没有意义", "活下去的意义", "活着有什么意义", "活着没意义",
            "撑不住", "熬不住", "撑不下去", "熬不下去", "撑不下去了",
            "消失", "想消失", "希望我消失", "如果我不在了",
            "解脱", "想要解脱",
            "不想醒来", "不想醒过来", "一觉不醒",
            "一了百了", "了断", "告别这个世界", "离开这个世界",
            "再见了", "最后的话", "告别一切", "想离开",
            // M-020: lethal-means / scene planning — ambiguous alone, but warrant a re-check
            // (which then applies the acute-crisis floor in SafetyReviewService).
            "药都准备好了", "准备好了药", "天台上", "站在窗边"
    );

    public boolean hasDistressSignal(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String signal : DISTRESS_SIGNALS) {
            if (text.contains(signal)) {
                return true;
            }
        }
        return false;
    }
}
