package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.common.Constants;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.User;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.service.UserService;
import com.innercosmos.vo.UserProfileVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Explicit classroom-demo entry. It is disabled by default, refuses to run with the prod
 * profile, and can only switch into the three curated non-admin showcase identities.
 */
@RestController
@RequestMapping("/api/public/demo")
public class DemoExperienceController {
    private static final List<DemoPersona> PERSONAS = List.of(
            new DemoPersona("lin-che", "Lin Che", "Turning ideals into something real",
                    "Four months where a course project, creative standards and the pressure to act became intertwined",
                    List.of("Making", "Self-expectation", "Real AI", "Boundaries")),
            new DemoPersona("shen-yan", "Shen Yan", "Finding herself again, far from home",
                    "Five months of exchange life, portfolio work and a slowly changing sense of loneliness",
                    List.of("Elsewhere", "Solitude", "Creative work", "Belonging")),
            new DemoPersona("xia-yu", "Xia Yu", "The person who always looks after everyone",
                    "Three months of a new job, family care and learning to rest without guilt",
                    List.of("Care", "Relationships", "Fatigue", "Personal boundaries"))
    );
    private static final Map<String, String> USERNAMES = Map.of(
            "lin-che", "demo",
            "shen-yan", "river",
            "xia-yu", "cloud");

    private final UserService userService;
    private final Environment environment;

    public DemoExperienceController(UserService userService, Environment environment) {
        this.userService = userService;
        this.environment = environment;
    }

    @GetMapping("/personas")
    public ApiResponse<List<DemoPersona>> personas() {
        return ApiResponse.ok(enabled() ? PERSONAS : List.of());
    }

    @PostMapping("/enter/{key}")
    public ApiResponse<UserProfileVO> enter(@org.springframework.web.bind.annotation.PathVariable String key,
                                             HttpServletRequest request) {
        if (!enabled()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "The Demo experience is not enabled");
        }
        String username = USERNAMES.get(key);
        if (username == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Demo persona not found");
        }
        User user = userService.findPublicDemoPersona(username);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "This Demo persona is not ready yet");
        }
        HttpSession session = request.getSession(true);
        // These three accounts are deliberately public, equal-privilege classroom stories. A
        // persona switch is not a credential elevation, so rotating the Redis-backed session id
        // here only creates a race between the completed switch, the next CSRF materialization
        // and the SPA's parallel bootstrap requests. Login/register still rotate normally.
        // Keeping this demo session stable preserves CSRF protection while making repeated
        // A -> B -> C comparisons reliable on web and APK.
        session.setAttribute(Constants.SESSION_USER_KEY, user.id);
        return ApiResponse.ok(UserProfileVO.from(user));
    }

    private boolean enabled() {
        boolean prod = java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod");
        return !prod && environment.getProperty(
                "inner-cosmos.demo.public-entry-enabled", Boolean.class, false);
    }

    public record DemoPersona(String key, String name, String headline,
                              String story, List<String> themes) {}
}
