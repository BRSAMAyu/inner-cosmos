package com.innercosmos.ai.goodbye;

import org.springframework.stereotype.Component;

/**
 * Chinese fallback farewell templates for the goodbye flow.
 * Used when LLM is unavailable or times out.
 */
@Component
public class FarewellTemplates {
    public String forTrigger(String trigger) {
        return switch (trigger) {
            case "BUTTON"         -> "谢谢你今天陪我聊了这么多。明天见。";
            case "LANGUAGE_HIGH"  -> "嗯，那今天先到这里。我会记住这段状态，晚安。";
            case "LANGUAGE_MEDIUM"-> "我感觉你可能想先停一下。要不要我把这段先收住？";
            case "IDLE"           -> "你回来时我还在。";
            default               -> "那我先在这里，等你回来。";
        };
    }

    public String forConfirm() {
        return "好，那我就先把这段收住了。";
    }
}