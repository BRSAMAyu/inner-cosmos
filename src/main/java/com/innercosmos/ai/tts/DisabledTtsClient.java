package com.innercosmos.ai.tts;

import java.util.List;

public class DisabledTtsClient implements TtsClient {
    @Override public boolean available() { return false; }

    /** Voice metadata is static catalog information; the settings UI can render it even offline. */
    @Override public List<TtsVoicePreset> voices() { return TtsVoicePresets.ALL; }

    @Override public byte[] synthesize(String text, String voiceId) {
        throw new IllegalStateException("tts provider is disabled");
    }
}
