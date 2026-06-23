package com.innercosmos.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.router.SessionModelRouter;
import com.innercosmos.common.ApiResponse;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.UserProfile;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.UserProfileMapper;
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

    private final UserProfileMapper userProfileMapper;
    private final SessionModelRouter router;

    public UserPreferenceController(UserProfileMapper userProfileMapper, SessionModelRouter router) {
        this.userProfileMapper = userProfileMapper;
        this.router = router;
    }

    /**
     * Set (or clear, when {@code provider} is null/blank) the default LLM provider
     * for the current user. A null/blank value falls back to the system default.
     */
    @PutMapping("/preferred-model")
    public ApiResponse<Boolean> setPreferredModel(@RequestBody Map<String, String> body, HttpSession session) {
        Long userId = currentUserId(session);
        // M-030: select by user_id (the FK), NOT by the profile's own PK id — selectById(userId)
        // loaded the wrong row (or null) for real users whose profile.id != user.id.
        UserProfile profile = userProfileMapper.selectOne(
                new QueryWrapper<UserProfile>().eq("user_id", userId).last("LIMIT 1"));
        if (profile == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "user not found");
        }
        String provider = body == null ? null : body.get("provider");
        profile.preferredModel = (provider == null || provider.isBlank()) ? null : provider.toUpperCase();
        userProfileMapper.updateById(profile);
        return ApiResponse.ok(true);
    }
}
