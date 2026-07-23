package com.innercosmos.controller;

import com.innercosmos.ai.tts.TtsClient;
import com.innercosmos.ai.tts.TtsVoicePreset;
import com.innercosmos.ai.tts.TtsVoicePresets;
import com.innercosmos.common.ApiResponse;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.UserProfile;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.service.UserService;
import com.innercosmos.vo.TtsPreferencesVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.Map;

/**
 * Per-user TTS voice + Aurora inner-voice (心声) delivery preferences, plus a settings-page
 * voice preview. Ships with inner-voice enabled by default (see {@code UserProfile}); this
 * controller only reads/writes the preference, actual composition + synthesis happens inline in
 * the Aurora turn stream (see {@code InnerVoiceComposer}, {@code AuroraAgentServiceImpl}).
 */
@RestController
@RequestMapping("/api/me/tts")
public class TtsController extends BaseController {
    private static final String PREVIEW_TEXT = "你好，我是你的内心声音，很高兴认识你。";

    private final TtsClient ttsClient;
    private final UserService userService;

    public TtsController(TtsClient ttsClient, UserService userService) {
        this.ttsClient = ttsClient;
        this.userService = userService;
    }

    @GetMapping("/voices")
    public ApiResponse<TtsPreferencesVO> voices(HttpSession session) {
        Long userId = currentUserId(session);
        UserProfile profile = userService.getProfile(userId);
        return ApiResponse.ok(toVO(profile));
    }

    @PatchMapping("/preferences")
    public ApiResponse<TtsPreferencesVO> updatePreferences(@RequestBody(required = false) TtsPreferencesPatch patch,
                                                           HttpSession session) {
        Long userId = currentUserId(session);
        String voiceId = patch == null ? null : patch.voiceId;
        if (voiceId != null && !voiceId.isBlank() && TtsVoicePresets.byId(voiceId).isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "unknown voiceId: " + voiceId);
        }
        String mode = patch == null ? null : patch.innerVoiceMode;
        if (mode != null && !mode.equals("AMBIENT") && !mode.equals("ON_DEMAND")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "innerVoiceMode must be AMBIENT or ON_DEMAND");
        }
        Boolean enabled = patch == null ? null : patch.innerVoiceEnabled;
        userService.updateTtsPreferences(userId, voiceId, enabled, mode);
        UserProfile profile = userService.getProfile(userId);
        return ApiResponse.ok(toVO(profile));
    }

    @PostMapping("/preview")
    public ApiResponse<Map<String, String>> preview(@RequestBody TtsPreviewRequest request) {
        String voiceId = request == null ? null : request.voiceId;
        TtsVoicePreset preset = TtsVoicePresets.byId(voiceId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "unknown voiceId: " + voiceId));
        if (!ttsClient.available()) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "tts provider is not configured");
        }
        byte[] audio = ttsClient.synthesize(PREVIEW_TEXT, preset.id());
        String dataUri = "data:audio/mpeg;base64," + Base64.getEncoder().encodeToString(audio);
        return ApiResponse.ok(Map.of("audio", dataUri));
    }

    private TtsPreferencesVO toVO(UserProfile profile) {
        TtsPreferencesVO vo = new TtsPreferencesVO();
        vo.voices = ttsClient.voices().stream().map(TtsPreferencesVO.VoiceVO::from).toList();
        String voiceId = profile == null ? null : profile.preferredTtsVoiceId;
        vo.currentVoiceId = (voiceId == null || voiceId.isBlank()) ? TtsVoicePresets.defaultVoice().id() : voiceId;
        vo.innerVoiceEnabled = profile == null || profile.innerVoiceEnabled == null || profile.innerVoiceEnabled;
        String mode = profile == null ? null : profile.innerVoiceMode;
        vo.innerVoiceMode = (mode == null || mode.isBlank()) ? "AMBIENT" : mode;
        return vo;
    }

    public static class TtsPreferencesPatch {
        public String voiceId;
        public Boolean innerVoiceEnabled;
        public String innerVoiceMode;
    }

    public static class TtsPreviewRequest {
        public String voiceId;
    }
}
