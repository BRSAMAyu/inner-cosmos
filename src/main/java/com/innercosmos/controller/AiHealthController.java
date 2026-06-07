package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.config.LlmConfig;
import com.innercosmos.entity.AiInteractionLog;
import com.innercosmos.service.AiLogService;
import com.innercosmos.vo.AiHealthVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiHealthController extends BaseController {
    private final LlmConfig llmConfig;
    private final AiLogService aiLogService;

    public AiHealthController(LlmConfig llmConfig, AiLogService aiLogService) {
        this.llmConfig = llmConfig;
        this.aiLogService = aiLogService;
    }

    @GetMapping("/health")
    public ApiResponse<AiHealthVO> health(HttpSession session) {
        currentUserId(session);
        AiHealthVO vo = new AiHealthVO();
        vo.failoverProviders = llmConfig.orderedProviderNames();
        vo.failoverModels = llmConfig.orderedProviderModels();
        vo.mode = llmConfig.getMode();
        vo.provider = llmConfig.activeProvider();
        vo.model = llmConfig.activeModel();
        vo.apiKeyConfigured = llmConfig.hasActiveApiKey();
        vo.fallbackAllowed = llmConfig.isEffectiveFallbackAllowed();
        vo.mockProvider = "mock".equalsIgnoreCase(vo.provider);
        vo.asrProvider = llmConfig.activeAsrProvider();
        vo.asrModel = llmConfig.activeAsrModel();
        vo.asrKeyConfigured = llmConfig.hasActiveAsrKey();
        vo.asrMockProvider = "mock".equalsIgnoreCase(vo.asrProvider);

        AiInteractionLog latest = aiLogService.latest();
        if (latest != null) {
            vo.lastSuccess = latest.success;
            vo.lastFallbackUsed = latest.fallbackUsed;
            vo.lastModule = latest.moduleName;
            vo.lastProvider = latest.provider;
            vo.lastModel = latest.modelName;
            vo.lastError = latest.errorMessage;
            vo.lastLatencyMs = latest.latencyMs;
        }
        return ApiResponse.ok(vo);
    }
}
