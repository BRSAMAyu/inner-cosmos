package com.innercosmos.controller;

import com.innercosmos.asr.AsrClient;
import com.innercosmos.asr.AsrResult;
import com.innercosmos.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/asr")
public class AsrController {
    private final AsrClient asrClient;

    public AsrController(AsrClient asrClient) {
        this.asrClient = asrClient;
    }

    @PostMapping("/mock-transcribe")
    public ApiResponse<AsrResult> mockTranscribe(@RequestBody Map<String, String> body) {
        String hint = body.getOrDefault("hintText", "");
        return ApiResponse.ok(asrClient.transcribe(hint.getBytes(StandardCharsets.UTF_8), hint));
    }
}
