package com.innercosmos.ai.lexicon;

import java.util.HashMap;
import java.util.Map;

/**
 * Chinese intensifiers (degree adverbs) for pseudo-semantic analysis.
 * Maps degree words to multipliers (0.5 to 2.0) that affect sentiment strength.
 */
public final class ChineseIntensifiers {

    private static final Map<String, Double> INTENSIFIERS = new HashMap<>();

    static {
        // === Strong intensifiers (1.5 - 2.0x) ===
        INTENSIFIERS.put("非常", 1.6);
        INTENSIFIERS.put("特别", 1.5);
        INTENSIFIERS.put("极其", 1.8);
        INTENSIFIERS.put("极度", 1.8);
        INTENSIFIERS.put("十分", 1.5);
        INTENSIFIERS.put("万分", 1.7);
        INTENSIFIERS.put("异常", 1.6);
        INTENSIFIERS.put("相当", 1.4);
        INTENSIFIERS.put("颇为", 1.3);
        INTENSIFIERS.put("甚", 1.4);
        INTENSIFIERS.put("超级", 1.7);
        INTENSIFIERS.put("极其", 1.8);
        INTENSIFIERS.put("格外", 1.5);
        INTENSIFIERS.put("尤其", 1.5);
        INTENSIFIERS.put("更是", 1.4);
        INTENSIFIERS.put("真是", 1.3);
        INTENSIFIERS.put("太", 1.5);
        INTENSIFIERS.put("好", 1.2);
        INTENSIFIERS.put("多", 1.2);

        // === Moderate intensifiers (1.2 - 1.4x) ===
        INTENSIFIERS.put("很", 1.3);
        INTENSIFIERS.put("挺", 1.2);
        INTENSIFIERS.put("蛮", 1.2);
        INTENSIFIERS.put("挺", 1.2);
        INTENSIFIERS.put("满", 1.2);
        INTENSIFIERS.put("蛮", 1.2);
        INTENSIFIERS.put("真", 1.2);
        INTENSIFIERS.put("确", 1.2);
        INTENSIFIERS.put("实", 1.2);
        INTENSIFIERS.put("实在", 1.3);
        INTENSIFIERS.put("确实", 1.3);
        INTENSIFIERS.put("的确", 1.3);
        INTENSIFIERS.put("真的", 1.3);
        INTENSIFIERS.put("真是", 1.3);
        INTENSIFIERS.put("总是", 1.2);
        INTENSIFIERS.put("一直", 1.2);
        INTENSIFIERS.put("都", 1.1);
        INTENSIFIERS.put("就", 1.1);

        // === Mild intensifiers (1.0 - 1.1x) ===
        INTENSIFIERS.put("有点", 1.0);
        INTENSIFIERS.put("有些", 1.0);
        INTENSIFIERS.put("一些", 1.0);
        INTENSIFIERS.put("稍微", 0.9);
        INTENSIFIERS.put("稍稍", 0.9);
        INTENSIFIERS.put("略", 0.9);
        INTENSIFIERS.put("略微", 0.9);
        INTENSIFIERS.put("多少", 1.0);
        INTENSIFIERS.put("相当", 1.2);

        // === Diminishers (0.5 - 0.9x) ===
        INTENSIFIERS.put("比较", 0.9);
        INTENSIFIERS.put("还算", 0.8);
        INTENSIFIERS.put("还算", 0.8);
        INTENSIFIERS.put("勉强", 0.7);
        INTENSIFIERS.put("多少", 0.9);
        INTENSIFIERS.put("有点儿", 0.9);
        INTENSIFIERS.put("不太", 0.7);
        INTENSIFIERS.put("不", 0.5);
        INTENSIFIERS.put("没", 0.5);
        INTENSIFIERS.put("无", 0.5);
        INTENSIFIERS.put("非", 0.5);
        INTENSIFIERS.put("别", 0.5);
    }

    private ChineseIntensifiers() {}

    /**
     * Get intensifier multiplier for a word. Returns 1.0 (no effect) if not found.
     * Multiplier range: 0.5 (diminishes) to 2.0 (strongly amplifies)
     */
    public static double getMultiplier(String word) {
        if (word == null || word.isBlank()) return 1.0;
        return INTENSIFIERS.getOrDefault(word.trim(), 1.0);
    }

    /**
     * Check if a word is an intensifier.
     */
    public static boolean isIntensifier(String word) {
        if (word == null || word.isBlank()) return false;
        return INTENSIFIERS.containsKey(word.trim());
    }

    /**
     * Check if intensifier is amplifying (multiplier > 1.0).
     */
    public static boolean isAmplifying(String word) {
        return isIntensifier(word) && getMultiplier(word) > 1.0;
    }

    /**
     * Check if intensifier is diminishing (multiplier < 1.0).
     */
    public static boolean isDiminishing(String word) {
        return isIntensifier(word) && getMultiplier(word) < 1.0;
    }

    /**
     * Get total intensifier count.
     */
    public static int size() {
        return INTENSIFIERS.size();
    }

    /**
     * All (word, multiplier) entries, for callers that scan raw text for substring occurrences
     * rather than looking up individual pre-tokenized words.
     */
    public static Map<String, Double> entries() {
        return java.util.Collections.unmodifiableMap(INTENSIFIERS);
    }
}
