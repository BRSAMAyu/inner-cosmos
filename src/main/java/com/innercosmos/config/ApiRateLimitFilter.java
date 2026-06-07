package com.innercosmos.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user API rate limiting filter.
 * Limits each user to 60 requests/minute and burst of 10.
 * For anonymous users, limits to 20 requests/minute.
 * Uses Bucket4j with in-memory storage (swap to Redis for multi-instance).
 */
@Component
public class ApiRateLimitFilter implements Filter {

    private static final int USER_LIMIT_PER_MINUTE = 60;
    private static final int ANON_LIMIT_PER_MINUTE = 20;
    private static final int BURST_CAPACITY = 10;

    private final Map<String, Bucket> userBuckets = new ConcurrentHashMap<>();
    private final Bucket anonBucket;

    public ApiRateLimitFilter() {
        // Anonymous bucket: 20/min, burst 5
        this.anonBucket = Bucket.builder()
            .addLimit(Bandwidth.simple(ANON_LIMIT_PER_MINUTE, Duration.ofMinutes(1)))
            .addLimit(Bandwidth.builder()
                .capacity(BURST_CAPACITY / 2)
                .refillGreedy(BURST_CAPACITY / 2, Duration.ofMinutes(1))
                .build())
            .build();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String path = req.getServletPath();
        // Skip rate limiting for health checks and static assets
        if (path.startsWith("/actuator/") || path.startsWith("/static/")) {
            chain.doFilter(request, response);
            return;
        }

        // Get user identifier
        String userId = req.getHeader("X-User-Id");
        Bucket bucket = userId != null && !userId.isBlank()
            ? userBuckets.computeIfAbsent(userId, this::createUserBucket)
            : anonBucket;

        if (bucket.tryConsume(1)) {
            // Add rate limit headers
            res.setHeader("X-RateLimit-Remaining", String.valueOf(bucket.getAvailableTokens()));
            res.setHeader("X-RateLimit-Limit", String.valueOf(
                userId != null ? USER_LIMIT_PER_MINUTE : ANON_LIMIT_PER_MINUTE));
            chain.doFilter(request, response);
        } else {
            res.setStatus(429);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"rate_limit_exceeded\",\"message\":\"请求过于频繁，请稍后再试。\",\"retry_after\":60}");
            res.setHeader("Retry-After", "60");
        }
    }

    private Bucket createUserBucket(String userId) {
        return Bucket.builder()
            .addLimit(Bandwidth.simple(USER_LIMIT_PER_MINUTE, Duration.ofMinutes(1)))
            .addLimit(Bandwidth.builder()
                .capacity(BURST_CAPACITY)
                .refillGreedy(BURST_CAPACITY, Duration.ofMinutes(1))
                .build())
            .build();
    }

    @Override
    public void init(FilterConfig filterConfig) {
        // Cleanup buckets older than 1 hour periodically (background job in production)
    }

    @Override
    public void destroy() {
        userBuckets.clear();
    }
}