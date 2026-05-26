package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.entity.AiInteractionLog;
import com.innercosmos.service.AiLogService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai-logs")
public class AiLogController extends BaseController {
    private final AiLogService aiLogService;

    public AiLogController(AiLogService aiLogService) {
        this.aiLogService = aiLogService;
    }

    @GetMapping
    public ApiResponse<List<AiInteractionLog>> list(HttpSession session) {
        return ApiResponse.ok(aiLogService.listRecent(currentUserId(session)));
    }
}
