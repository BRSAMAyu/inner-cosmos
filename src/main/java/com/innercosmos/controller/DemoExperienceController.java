package com.innercosmos.controller;

import com.innercosmos.common.ApiResponse;
import com.innercosmos.common.Constants;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.User;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.service.DemoSandboxService;
import com.innercosmos.service.UserService;
import com.innercosmos.vo.UserProfileVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * Explicit classroom-demo entry. It is disabled by default, refuses to run with the prod
 * profile, and creates an owner-isolated copy of one of three curated story templates.
 */
@RestController
@RequestMapping("/api/public/demo")
public class DemoExperienceController {
    private static final List<DemoPersona> PERSONAS = List.of(
            new DemoPersona("lin-che", "Lin Che", "Turning ideals into something real",
                    "Four months where a course project, creative standards and the pressure to act became intertwined",
                    List.of("Making", "Self-expectation", "Real AI", "Boundaries"), false),
            new DemoPersona("shen-yan", "Shen Yan", "Finding herself again, far from home",
                    "Five months of exchange life, portfolio work and a slowly changing sense of loneliness",
                    List.of("Elsewhere", "Solitude", "Creative work", "Belonging"), false),
            new DemoPersona("xia-yu", "Xia Yu", "The person who always looks after everyone",
                    "Three months of a new job, family care and learning to rest without guilt",
                    List.of("Care", "Relationships", "Fatigue", "Personal boundaries"), false)
    );
    private static final String SANDBOX_USERS = DemoExperienceController.class.getName() + ".sandboxUsers";

    private final UserService userService;
    private final ObjectProvider<DemoSandboxService> sandboxServices;
    private final Environment environment;

    public DemoExperienceController(UserService userService, ObjectProvider<DemoSandboxService> sandboxServices,
                                    Environment environment) {
        this.userService = userService;
        this.sandboxServices = sandboxServices;
        this.environment = environment;
    }

    @GetMapping("/personas")
    public ApiResponse<List<DemoPersona>> personas(HttpServletRequest request) {
        if (!enabled() || sandboxServices.getIfAvailable() == null
                || !mayUseDemoEntry(request.getSession(false))) {
            return ApiResponse.ok(List.of());
        }
        Long currentUserId = currentUserId(request.getSession(false));
        Map<String, Long> sandboxes = sandboxUsers(request.getSession(false), false);
        return ApiResponse.ok(PERSONAS.stream().map(persona -> new DemoPersona(
                persona.key(), persona.name(), persona.headline(), persona.story(), persona.themes(),
                currentUserId != null && currentUserId.equals(sandboxes.get(persona.key())))).toList());
    }

    @PostMapping("/enter/{key}")
    public ApiResponse<UserProfileVO> enter(@org.springframework.web.bind.annotation.PathVariable String key,
                                             HttpServletRequest request) {
        if (!enabled()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "The Demo experience is not enabled");
        }
        if (PERSONAS.stream().noneMatch(persona -> persona.key().equals(key))) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Demo persona not found");
        }
        HttpSession existingSession = request.getSession(false);
        boolean crossingAuthenticationBoundary = currentUserId(existingSession) == null;
        if (!mayUseDemoEntry(existingSession)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Story templates are available before sign-up or inside a Demo sandbox");
        }
        HttpSession session = request.getSession(true);
        User user;
        synchronized (session) {
            Map<String, Long> sandboxes = sandboxUsers(session, true);
            Long sandboxId = sandboxes.get(key);
            if (sandboxId == null) {
                DemoSandboxService sandboxService = sandboxServices.getIfAvailable();
                if (sandboxService == null) {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "This Demo story is not ready yet");
                }
                user = sandboxService.createPersonalSandbox(key);
                sandboxes.put(key, user.id);
                session.setAttribute(SANDBOX_USERS, new HashMap<>(sandboxes));
            } else {
                user = userService.current(sandboxId);
            }
            if (crossingAuthenticationBoundary) {
                request.changeSessionId();
            }
            session.setAttribute(Constants.SESSION_USER_KEY, user.id);
        }
        return ApiResponse.ok(UserProfileVO.from(user));
    }

    private boolean mayUseDemoEntry(HttpSession session) {
        Long userId = currentUserId(session);
        if (userId == null) return true;
        try {
            User user = userService.current(userId);
            return "DEMO".equals(user.accountKind) || "SHOWCASE".equals(user.accountKind)
                    || "SANDBOX".equals(user.accountKind);
        } catch (BusinessException ignored) {
            return false;
        }
    }

    private static Long currentUserId(HttpSession session) {
        if (session == null) return null;
        Object value = session.getAttribute(Constants.SESSION_USER_KEY);
        return value instanceof Long id ? id : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Long> sandboxUsers(HttpSession session, boolean create) {
        if (session == null) return Map.of();
        Object value = session.getAttribute(SANDBOX_USERS);
        if (value instanceof Map<?, ?> rows) {
            Map<String, Long> result = new HashMap<>();
            rows.forEach((key, id) -> {
                if (key instanceof String stringKey && id instanceof Long longId) {
                    result.put(stringKey, longId);
                }
            });
            return result;
        }
        return create ? new HashMap<>() : Map.of();
    }

    private boolean enabled() {
        boolean prod = java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod");
        return !prod && environment.getProperty(
                "inner-cosmos.demo.public-entry-enabled", Boolean.class, false);
    }

    public record DemoPersona(String key, String name, String headline,
                              String story, List<String> themes, boolean active) {}
}
