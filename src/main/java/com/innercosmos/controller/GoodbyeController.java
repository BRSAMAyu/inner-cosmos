package com.innercosmos.controller;

import com.innercosmos.ai.goodbye.GoodbyeOrchestrator;
import com.innercosmos.ai.goodbye.GoodbyeResult;
import com.innercosmos.common.ApiResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for the goodbye flow.
 * Endpoint: POST /api/aurora/goodbye
 */
@RestController
@RequestMapping("/api/aurora/goodbye")
public class GoodbyeController extends BaseController {

    @Autowired
    private GoodbyeOrchestrator orchestrator;

    @PostMapping
    public ApiResponse<GoodbyeResult> trigger(@RequestBody Map<String, String> body, HttpSession session) {
        Long userId = currentUserId(session);
        String trigger = body.getOrDefault("trigger", "BUTTON");
        Long sessionId = body.get("sessionId") == null ? null : Long.parseLong(body.get("sessionId"));
        GoodbyeResult result = orchestrator.start(userId, sessionId, trigger);
        return ApiResponse.ok(result);
    }
}