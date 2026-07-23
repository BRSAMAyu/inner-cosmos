package com.innercosmos.ai.tts;

import java.util.List;

/**
 * Text-to-speech synthesis. Mirrors the shape of {@code MemoryEmbeddingClient}: a real
 * implementation ({@link QwenAudioTtsClient}) and a no-op {@link DisabledTtsClient} fallback so
 * every caller can be written the same way regardless of whether a real credential is configured.
 */
public interface TtsClient {
    boolean available();

    /** The fixed preset voice catalog (see {@link TtsVoicePresets}) -- available even when {@link #available()} is false. */
    List<TtsVoicePreset> voices();

    /**
     * Synthesize {@code text} in the given preset voice.
     *
     * @return raw audio bytes (MP3)
     * @throws IllegalStateException if this client is not available (see {@link #available()})
     * @throws IllegalArgumentException if {@code voiceId} does not match a known preset
     */
    byte[] synthesize(String text, String voiceId);
}
