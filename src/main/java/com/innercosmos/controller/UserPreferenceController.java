package com.innercosmos.controller;

import com.innercosmos.ai.router.SessionModelRouter;
import com.innercosmos.common.ApiResponse;
import com.innercosmos.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Per-user LLM preferences. The default {@code llmClient} bean stays untouched
 * — this controller only updates {@code tb_user_profile.preferred_model}, which
 * is consumed by {@link SessionModelRouter}.
 *
 * <p>The router is injected per the M6 spec so future per-user routing
 * extensions (validation, audit logging) can hook in here. The
 * session-level endpoint lives in {@code AuroraChatController}.
 */
@RestController
@RequestMapping("/api/user")
public class UserPreferenceController extends BaseController {

    private final UserService userService;
    private final SessionModelRouter router;

    public UserPreferenceController(UserService userService, SessionModelRouter router) {
        this.userService = userService;
        this.router = router;
    }

    /**
     * Set (or clear, when {@code provider} is null/blank) the default LLM provider
     * for the current user. A null/blank value falls back to the system default.
     */
    @PutMapping("/preferred-model")
    public ApiResponse<Boolean> setPreferredModel(@RequestBody Map<String, String> body, HttpSession session) {
        Long userId = currentUserId(session);
        String provider = body == null ? null : body.get("provider");
        userService.setPreferredModel(userId, provider);
        return ApiResponse.ok(true);
    }
}
