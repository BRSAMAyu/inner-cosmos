package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.entity.AiInteractionLog;
import com.innercosmos.service.AiLogService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    // Regression (Gemini audit / remaining-work-handoff.md 2.2.5): this only ever called
    // currentUserId(session), never requireAdmin(session), despite this endpoint being
    // documented and consumed exclusively as an admin moderation view (AdminAiLogsTab.tsx is
    // its only frontend caller). Passing the caller's own id as the userId filter also meant an
    // admin could only ever see their OWN AI interactions, defeating the point of a system-wide
    // log view -- pass null so every user's interactions are visible to admins, same as every
    // other AdminController list endpoint (users/capsules/reports/safety-events/audit-logs).
    @GetMapping
    public ApiResponse<List<AiInteractionLog>> list(@RequestParam(required = false) String module,
                                                     @RequestParam(required = false) String provider,
                                                     @RequestParam(required = false) Boolean success,
                                                     HttpSession session) {
        requireAdmin(session);
        return ApiResponse.ok(aiLogService.listRecent(null, module, provider, success));
    }
}
