package com.innercosmos.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.common.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * CP-62 停服/危机运行手册的"先保只读"技术能力（蓝图 L894）：readonly 模式暂停新的
 * 内容产生与收费入口，但法定与用户义务路径保持可用——
 * <ul>
 *   <li>全部 GET/HEAD/OPTIONS（浏览、导出、权益查看不受影响）</li>
 *   <li>登录（用户必须能登录才能导出自己的数据）与安全资源</li>
 *   <li>数据权利路径（撤回/删除是义务，不是新内容）与注销</li>
 *   <li>支付渠道回调（停服期间渠道仍在动钱，事实必须继续入账本）与退款取消
 *       （退款是义务）</li>
 * </ul>
 * 其余写请求（新对话/慢信/共鸣体/社交/注册/下单）→ 503 + 明确文案：本地数据与
 * 导出权利不受影响。开关由 operator 在停服演练/真实事件时打开
 * （inner-cosmos.readonly-mode.enabled，默认 false——开关关闭时本过滤器不注册）。
 */
@Component
@ConditionalOnProperty(name = "inner-cosmos.readonly-mode.enabled", havingValue = "true")
public class ReadOnlyModeFilter extends OncePerRequestFilter {

    /** Obligation paths keep working in readonly mode (prefix matches). */
    private static final List<String> ALLOWED_WRITE_PREFIXES = List.of(
            "/api/auth/login", "/api/v1/auth/login", "/api/auth/csrf", "/api/v1/auth/csrf",
            "/api/safety/", "/api/public/",
            "/api/me/data-rights/", "/api/v1/data-rights/",
            "/api/me/data/export", "/api/me/entitlements/", "/api/me/quotas",
            "/api/payments/callbacks/",
            "/api/notifications");

    private static final ObjectMapper JSON = new ObjectMapper();

    @Value("${inner-cosmos.readonly-mode.message:服务进入只读模式：暂停新的内容产生与收费入口。你的本地数据、导出与数据权利不受影响。}")
    private String message;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {
        String method = request.getMethod() == null ? "" : request.getMethod().toUpperCase(Locale.ROOT);
        String path = request.getServletPath();
        if (path == null || path.isBlank()) {
            path = request.getRequestURI() == null ? "" : request.getRequestURI();
        }
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)
                || isObligationPath(path)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType("application/json;charset=UTF-8");
        ApiResponse<Void> body = ApiResponse.fail("READONLY_MODE", message);
        response.getWriter().write(JSON.writeValueAsString(body));
    }

    private static boolean isObligationPath(String path) {
        return ALLOWED_WRITE_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
