package com.innercosmos.controller;

import com.innercosmos.asr.AsrClient;
import com.innercosmos.asr.AsrResult;
import com.innercosmos.common.ApiResponse;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.exception.BusinessException;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/asr")
public class AsrController extends BaseController {
    /** CP-27 voice consent gate; optional so direct-construction tests keep working. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.innercosmos.service.consent.ConsentCenterService consentCenter;

    private final AsrClient asrClient;

    public AsrController(AsrClient asrClient) {
        this.asrClient = asrClient;
    }

    @PostMapping("/mock-transcribe")
    public ApiResponse<AsrResult> mockTranscribe(@RequestBody Map<String, String> body, HttpSession session) {
        currentUserId(session);
        String hint = body.getOrDefault("hintText", "");
        return ApiResponse.ok(asrClient.transcribe(hint.getBytes(StandardCharsets.UTF_8), hint));
    }

    @PostMapping("/transcribe")
    public ApiResponse<AsrResult> transcribe(@RequestParam("file") MultipartFile file, HttpSession session) throws IOException {
        Long userId = currentUserId(session);
        // CP-27: voice is a separate (biometric-adjacent) consent — the real egress path is
        // gated; the mock path stays local and ungated. No consent -> no audio leaves.
        if (consentCenter != null) {
            consentCenter.assertVoiceProcessing(userId);
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "音频文件不能为空");
        }
        if (file.getSize() > 25L * 1024L * 1024L) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "音频文件不能超过 25MB");
        }
        return ApiResponse.ok(asrClient.transcribe(file.getBytes(), ""));
    }
}
