package com.innercosmos.ai.tts;

import java.util.List;
import java.util.Optional;

/**
 * A separate, isolated catalog of preset voices used when a visitor chats with an Echo Capsule
 * (the {@code /api/persona-chat/.../voice} path), deliberately distinct from Aurora's own
 * {@link TtsVoicePresets} (which drive Aurora's "inner voice" / 心声 and the per-user voice
 * settings page).
 *
 * <p><b>Why a separate catalog (product intent):</b> when a visitor HEARS a capsule reply spoken
 * aloud, the voice must read as a different persona from Aurora's warm/feminine defaults -- it
 * reinforces "this is someone else's authorized echo, not your Aurora." Keeping these IDs out of
 * {@code TtsVoicePresets.ALL} also keeps the Aurora settings picker ({@code GET /api/me/tts/voices})
 * from offering capsule persona voices to users as a personal Aurora voice.
 *
 * <p>Each entry's {@code model}/{@code providerVoice} pair was confirmed synthesizing against the
 * real Aliyun account during the INNO-INNER-013 spike (see {@code evidence/innovation/INNO-INNER-013/README.md})
 * but was NOT already used by {@link TtsVoicePresets}, so the two catalogs stay disjoint.
 *
 * <p>As with {@code TtsVoicePresets}: descriptive labels reflect Aliyun's published voice-persona
 * naming for these system voices, not an independent listening review.
 */
public final class CapsuleVoicePresets {
    private CapsuleVoicePresets() {
    }

    public static final List<TtsVoicePreset> ALL = List.of(
        new TtsVoicePreset("capsule_calm_neutral", "平和回声 · 书", "zh",
            "我是来自另一个人的回声，很高兴在这里与你相遇。", "cosyvoice-v2", "longshu_v2"),
        new TtsVoicePreset("capsule_deep_steady", "沉稳低音 · 远", "zh",
            "我在这里，慢慢说，这一段话值得被认真听到。", "cosyvoice-v2", "longyuan_v2")
    );

    public static Optional<TtsVoicePreset> byId(String id) {
        if (id == null) return Optional.empty();
        return ALL.stream().filter(v -> v.id().equals(id)).findFirst();
    }

    /** The capsule persona voice used by default when a visitor taps play on a capsule reply. */
    public static TtsVoicePreset defaultVoice() {
        return ALL.get(0);
    }
}
