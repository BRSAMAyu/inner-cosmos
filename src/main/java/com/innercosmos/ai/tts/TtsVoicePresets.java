package com.innercosmos.ai.tts;

import java.util.List;
import java.util.Optional;

/**
 * The fixed catalog of preset voices this product offers. Every entry was empirically confirmed
 * against the real Aliyun DashScope/private-gateway account during the W1 TTS spike: each
 * (model, providerVoice) pair authenticated and returned non-empty synthesized MP3 audio over the
 * {@code wss://.../api-ws/v1/inference} run-task/continue-task/finish-task protocol (see
 * {@code evidence/innovation/INNO-INNER-013/README.md}). Descriptive labels reflect Aliyun's
 * published voice-persona naming for these system voices, not a from-scratch listening review.
 *
 * <p>Deliberately a plain static list rather than {@code application.yml}-configurable: these are
 * specific vendor (model, voice) pairs, not values an operator should casually edit without
 * re-confirming they still authenticate.
 */
public final class TtsVoicePresets {
    private TtsVoicePresets() {
    }

    public static final List<TtsVoicePreset> ALL = List.of(
        new TtsVoicePreset("warm_gentle_female", "温柔女声 · 小春", "zh",
            "你好呀，很高兴听到你的声音。", "cosyvoice-v2", "longxiaochun_v2"),
        new TtsVoicePreset("calm_steady_female", "沉稳女声 · 婉儿", "zh",
            "我在这里，慢慢说，不着急。", "cosyvoice-v2", "longwan_v2"),
        new TtsVoicePreset("deep_soothing_male", "低沉男声 · 成然", "zh",
            "有些话不必大声说出来，我陪你。", "cosyvoice-v2", "longcheng_v2"),
        new TtsVoicePreset("bright_young_female", "明亮女声 · 悦悦", "zh",
            "嗨，今天想聊点什么呀？", "cosyvoice-v3-flash", "longyue_v3"),
        new TtsVoicePreset("bright_young_male", "阳光男声 · 昂扬", "zh",
            "嘿，我一直都在，随时找我聊聊。", "cosyvoice-v3-flash", "longanyang"),
        new TtsVoicePreset("warm_expressive_female", "温暖女声（表现力）· 安欢", "zh",
            "你好，我是你内心的声音，很高兴认识你。", "qwen-audio-3.0-tts-flash", "longanhuan_v3.6")
    );

    public static Optional<TtsVoicePreset> byId(String id) {
        if (id == null) return Optional.empty();
        return ALL.stream().filter(v -> v.id().equals(id)).findFirst();
    }

    public static TtsVoicePreset defaultVoice() {
        return ALL.get(0);
    }
}
