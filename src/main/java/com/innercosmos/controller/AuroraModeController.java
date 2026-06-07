package com.innercosmos.controller;

import com.innercosmos.ai.mode.ModeRegistry;
import com.innercosmos.ai.mode.ModeStrategy;
import com.innercosmos.ai.mode.ModeSwitchService;
import com.innercosmos.common.ApiResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for Aurora mode switching.
 */
@RestController
@RequestMapping("/api/aurora/mode")
public class AuroraModeController extends BaseController {

    private final ModeSwitchService modeSwitchService;
    private final ModeRegistry modeRegistry;

    public AuroraModeController(ModeSwitchService modeSwitchService, ModeRegistry modeRegistry) {
        this.modeSwitchService = modeSwitchService;
        this.modeRegistry = modeRegistry;
    }

    /**
     * List all available mode names.
     */
    @GetMapping("/modes")
    public ApiResponse<java.util.List<String>> modes() {
        return ApiResponse.ok(modeRegistry.names());
    }

    /**
     * Switch Aurora to a different conversation mode.
     * POST /api/aurora/mode/switch
     * Body: { sessionId: Long, mode: String }
     */
    @PostMapping("/switch")
    public ApiResponse<Map<String, Object>> switchMode(@RequestBody Map<String, Object> body, HttpSession session) {
        Long userId = currentUserId(session);
        Long sessionId = parseSessionId(body.get("sessionId"));
        String mode = parseMode(body.get("mode"));

        ModeStrategy strategy = modeSwitchService.switchTo(userId, sessionId, mode);

        return ApiResponse.ok(Map.of(
            "mode", strategy.name(),
            "segment", strategy.segment(),
            "temperature", strategy.temperature(),
            "ackRequired", strategy.requiresMultiTurnAcknowledgement()
        ));
    }

    private Long parseSessionId(Object value) {
        if (value == null) {
            throw new com.innercosmos.exception.BusinessException(
                com.innercosmos.common.ErrorCode.BAD_REQUEST, "sessionId is required");
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }

    private String parseMode(Object value) {
        if (value == null) {
            throw new com.innercosmos.exception.BusinessException(
                com.innercosmos.common.ErrorCode.BAD_REQUEST, "mode is required");
        }
        return value.toString().toUpperCase().trim();
    }
}