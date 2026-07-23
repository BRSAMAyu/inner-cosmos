package com.innercosmos.vo;

import com.innercosmos.ai.tts.TtsVoicePreset;

import java.util.List;

/** Response shape for {@code GET /api/me/tts/voices} and {@code PATCH /api/me/tts/preferences}. */
public class TtsPreferencesVO {
    public List<VoiceVO> voices;
    public String currentVoiceId;
    public boolean innerVoiceEnabled;
    public String innerVoiceMode;

    public static class VoiceVO {
        public String id;
        public String label;
        public String language;
        public String previewText;

        public static VoiceVO from(TtsVoicePreset preset) {
            VoiceVO vo = new VoiceVO();
            vo.id = preset.id();
            vo.label = preset.label();
            vo.language = preset.language();
            vo.previewText = preset.previewText();
            return vo;
        }
    }
}
