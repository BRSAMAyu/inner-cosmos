package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.service.AuroraAgentService;
import com.innercosmos.service.AuroraProactiveService;
import com.innercosmos.vo.AuroraReplyVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for Aurora's proactive greeting feature.
 * B5: 主动呼唤（Quiet Hours 内禁用）
 */
@RestController
@RequestMapping("/api/aurora/proactive")
public class AuroraProactiveController extends BaseController {

    private final AuroraProactiveService proactiveService;
    private final AuroraAgentService auroraAgentService;

    public AuroraProactiveController(AuroraProactiveService proactiveService,
                                     AuroraAgentService auroraAgentService) {
        this.proactiveService = proactiveService;
        this.auroraAgentService = auroraAgentService;
    }

    @PostMapping("/check")
    public ApiResponse<Map<String, Object>> check(@RequestParam(required = false) Long hoursSinceLastSession,
                                                  HttpSession session) {
        Long userId = currentUserId(session);
        AuroraProactiveService.ProactiveGreeting g = proactiveService.evaluate(
                userId, LocalDateTime.now(), hoursSinceLastSession);
        Map<String, Object> data = new HashMap<>();
        if (g == null) {
            data.put("shouldGreet", false);
            data.put("reason", hoursSinceLastSession == null
                    ? "no prior session"
                    : (hoursSinceLastSession < 24 ? "too soon" : "quiet hours or muted"));
        } else {
            AuroraReplyVO reply = auroraAgentService.generateGreeting(userId, null, "DAILY_TALK");
            data.put("shouldGreet", true);
            data.put("greeting", reply.messages == null || reply.messages.isEmpty() ? g.greeting : reply.messages.get(0));
            data.put("reply", reply);
            data.put("hoursSinceLastSession", g.hoursSinceLastSession);
        }
        return ApiResponse.ok(data);
    }

    @PostMapping("/{id}/dismiss")
    public ApiResponse<Void> dismiss(@PathVariable Long id, HttpSession session) {
        // In a real system we'd persist a "dismissed_at" timestamp so Aurora doesn't
        // re-greet for some time. For now we just acknowledge.
        return ApiResponse.ok(null);
    }
}
