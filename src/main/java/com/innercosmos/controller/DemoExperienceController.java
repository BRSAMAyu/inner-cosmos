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
            new DemoPersona("lin-che", "林澈", "把理想做成作品的人",
                    "课程项目、审美坚持与行动压力交织的四个月",
                    List.of("创造", "自我要求", "真实 AI", "边界")),
            new DemoPersona("shen-yan", "沈砚", "在异乡重新辨认自己",
                    "交换生活、作品集与孤独感缓慢变化的五个月",
                    List.of("异乡", "独处", "创作", "归属")),
            new DemoPersona("xia-yu", "夏榆", "总在照顾别人的人",
                    "新工作、家庭照护与不带愧疚地休息的三个月",
                    List.of("照护", "关系", "疲惫", "自我边界"))
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
            throw new BusinessException(ErrorCode.NOT_FOUND, "Demo 体验入口未开启");
        }
        String username = USERNAMES.get(key);
        if (username == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "未找到这个体验角色");
        }
        User user = userService.findPublicDemoPersona(username);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "体验角色尚未准备好");
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
