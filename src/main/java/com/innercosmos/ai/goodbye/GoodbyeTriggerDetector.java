package com.innercosmos.ai.goodbye;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Detects goodbye intent from user messages with 3 confidence tiers.
 * - HIGH: explicit goodbye phrases, no confirmation needed
 * - MEDIUM: ambiguous phrases, confirmation needed
 * - NONE: no goodbye detected
 */
@Component
public class GoodbyeTriggerDetector {
    private static final List<String> HIGH = List.of(
            "我先睡了", "晚安", "先这样", "我走了", "今天到这吧",
            "不聊了", "拜拜", "再见", "明天见", "回见"
    );
    private static final List<String> MEDIUM = List.of(
            "有点累", "算了", "不想说了", "先放着吧",
            "可能之后再聊", "之后再聊", "回头聊", "下次再说"
    );

    public record Detection(String trigger, double confidence, boolean needsConfirm) {}
    public static final Detection NONE = new Detection(null, 0.0, false);

    // M-045: per-thread strength. The detector is a singleton shared across concurrent
    // requests, so a plain field would let one user's detect() clobber another's strength
    // before getLastStrength() is read. detect() always sets before the same-thread read.
    private final ThreadLocal<String> lastStrength = ThreadLocal.withInitial(() -> "NONE");

    public Detection detect(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            lastStrength.set("NONE");
            return NONE;
        }
        String m = userMessage.trim();
        if (HIGH.stream().anyMatch(m::contains)) {
            lastStrength.set("HIGH");
            return new Detection("LANGUAGE_HIGH", 0.95, false);
        }
        if (MEDIUM.stream().anyMatch(m::contains)) {
            lastStrength.set("MEDIUM");
            return new Detection("LANGUAGE_MEDIUM", 0.65, true);
        }
        lastStrength.set("NONE");
        return NONE;
    }

    public String getLastStrength() {
        return lastStrength.get();
    }
}