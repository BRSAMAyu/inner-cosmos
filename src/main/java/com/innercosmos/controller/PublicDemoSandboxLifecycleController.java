package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.common.Constants;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.User;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

/**
 * Lets an automated classroom rehearsal remove only the owner-isolated sandbox attached to its
 * own browser session. It cannot delete curated templates or ordinary registered users.
 */
@RestController
@RequestMapping("/api/public/demo")
public class PublicDemoSandboxLifecycleController extends BaseController {
    private final Environment environment;

    public PublicDemoSandboxLifecycleController(Environment environment) {
        this.environment = environment;
    }

    @DeleteMapping("/sandbox")
    public ApiResponse<Boolean> deleteCurrentSandbox(HttpSession session) {
        if (!enabled()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "The Demo experience is not enabled");
        }
        Long userId = currentUserId(session);
        User user = userService.current(userId);
        if (!"SANDBOX".equals(user.accountKind)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Only the current isolated Demo sandbox can be removed");
        }
        userService.deleteAccount(userId);
        session.invalidate();
        return ApiResponse.ok(true);
    }

    private boolean enabled() {
        return !Arrays.asList(environment.getActiveProfiles()).contains("prod")
                && environment.getProperty(
                "inner-cosmos.demo.public-entry-enabled", Boolean.class, false);
    }
}
