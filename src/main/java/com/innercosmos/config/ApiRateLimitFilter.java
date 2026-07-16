package com.innercosmos.config;

import com.innercosmos.common.Constants;
import com.innercosmos.common.ApiErrorResponse;
import com.innercosmos.entity.User;
import com.innercosmos.ratelimit.RateLimitDecision;
import com.innercosmos.ratelimit.RateLimitKey;
import com.innercosmos.ratelimit.RateLimitPolicy;
import com.innercosmos.ratelimit.RateLimitProperties;
import com.innercosmos.ratelimit.RateLimitStore;
import com.innercosmos.ratelimit.RateLimitStoreUnavailableException;
import com.innercosmos.util.JsonUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Application-level abuse protection. The filter runs after bearer/session authentication,
 * so every verified identity shares one quota across pods and authentication mechanisms.
 */
@Component
public final class ApiRateLimitFilter extends OncePerRequestFilter {

    private final RateLimitStore store;
    private final RateLimitProperties properties;

    @Value("${inner-cosmos.security.trusted-proxy-enabled:false}")
    private boolean trustedProxyConfigured;

    public ApiRateLimitFilter(RateLimitStore store, RateLimitProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {
        String path = requestPath(request);
        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        try {
            if (isLoginAttempt(request, path)) {
                if (!consume(response, "login", clientIp(request), properties.login())) {
                    writeExceeded(response, "登录尝试过于频繁，请稍后再试。");
                    return;
                }
            }

            if (path.startsWith("/api/auth/") || path.startsWith("/api/v1/auth/")) {
                chain.doFilter(request, response);
                return;
            }

            boolean aurora = isAuroraLlm(path);
            if ("GET".equalsIgnoreCase(request.getMethod()) && !aurora) {
                chain.doFilter(request, response);
                return;
            }

            String userId = authenticatedUserId(request);
            RateLimitPolicy policy;
            String scope;
            String subject;
            if (userId != null) {
                policy = aurora ? properties.aurora() : properties.user();
                scope = aurora ? "aurora" : "user";
                subject = userId;
            } else {
                policy = properties.anonymous();
                scope = aurora ? "anonymous-aurora" : "anonymous";
                subject = clientIp(request);
            }

            if (!consume(response, scope, subject, policy)) {
                writeExceeded(response, "请求过于频繁，请稍后再试。");
                return;
            }
            chain.doFilter(request, response);
        } catch (RateLimitStoreUnavailableException unavailable) {
            response.setHeader("Retry-After", "5");
            writeJson(response, ApiErrorResponse.of("RATE_LIMIT_UNAVAILABLE",
                    "请求保护服务暂时不可用，请稍后重试。", 503, java.util.Map.of("retryAfter", 5)));
        }
    }

    private boolean consume(HttpServletResponse response,
                            String scope,
                            String subject,
                            RateLimitPolicy policy) {
        RateLimitDecision decision = store.consume(RateLimitKey.forSubject(scope, subject), policy);
        response.setHeader("X-RateLimit-Limit", String.valueOf(policy.advertisedLimit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remainingTokens()));
        return decision.allowed();
    }

    private String authenticatedUserId(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof User user && user.id != null) {
            return user.id.toString();
        }
        var session = request.getSession(false);
        if (session != null) {
            Object userId = session.getAttribute(Constants.SESSION_USER_KEY);
            if (userId instanceof Long value && value > 0) {
                return value.toString();
            }
        }
        return null;
    }

    private boolean isLoginAttempt(HttpServletRequest request, String path) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && ("/api/auth/login".equals(path) || "/api/v1/auth/login".equals(path));
    }

    private String requestPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (uri == null) {
            return "";
        }
        return contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
                ? uri.substring(contextPath.length()) : uri;
    }

    private boolean isAuroraLlm(String path) {
        String normalized = path.replaceFirst("^/api/v1/", "/api/");
        return normalized.startsWith("/api/aurora/chat")
                || normalized.startsWith("/api/aurora/stream")
                || normalized.startsWith("/api/aurora/greeting")
                || normalized.startsWith("/api/aurora/message");
    }

    private String clientIp(HttpServletRequest request) {
        if (trustedProxyConfigured) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",", 2)[0].trim();
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }

    private void writeExceeded(HttpServletResponse response, String message) throws IOException {
        response.setHeader("Retry-After", "60");
        writeJson(response, ApiErrorResponse.of("RATE_LIMIT_EXCEEDED", message, 429,
                java.util.Map.of("retryAfter", 60)));
    }

    private void writeJson(HttpServletResponse response, ApiErrorResponse error) throws IOException {
        response.setStatus(error.status());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write(JsonUtils.toJson(error));
    }
}
