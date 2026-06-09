package com.innercosmos.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user API rate limiting filter.
 * Limits each user to 60 requests/minute and burst of 10.
 * For anonymous users, limits to 20 requests/minute.
 * Aurora LLM endpoints limited to 20 requests/minute per user.
 * Uses Bucket4j with in-memory storage (swap to Redis for multi-instance).
 */
@Component
public class ApiRateLimitFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(ApiRateLimitFilter.class);

    private static final int USER_LIMIT_PER_MINUTE = 60;
    private static final int ANON_LIMIT_PER_MINUTE = 20;
    private static final int AURORA_LLM_LIMIT_PER_MINUTE = 20;
    private static final int BURST_CAPACITY = 10;

    private final Map<String, BucketEntry> userBuckets = new ConcurrentHashMap<>();
    private final Bucket anonBucket;

    public ApiRateLimitFilter() {
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
        if (path.startsWith("/actuator/") || path.startsWith("/static/")) {
            chain.doFilter(request, response);
            return;
        }

        String userId = req.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) {
            var session = req.getSession(false);
            if (session != null) {
                Object uid = session.getAttribute("userId");
                if (uid != null) userId = uid.toString();
            }
        }
        boolean isAuroraLlm = path.startsWith("/api/aurora/chat") ||
                              path.startsWith("/api/aurora/stream") ||
                              path.startsWith("/api/aurora/greeting");

        Bucket bucket;
        if (userId != null && !userId.isBlank()) {
            String key = isAuroraLlm ? "llm:" + userId : userId;
            BucketEntry entry = userBuckets.compute(key, (k, existing) -> {
                if (existing == null) return isAuroraLlm ? createAuroraBucket() : createUserBucket();
                existing.lastAccess = System.currentTimeMillis();
                return existing;
            });
            bucket = entry.bucket;
        } else {
            bucket = anonBucket;
        }

        if (bucket.tryConsume(1)) {
            res.setHeader("X-RateLimit-Remaining", String.valueOf(bucket.getAvailableTokens()));
            res.setHeader("X-RateLimit-Limit", String.valueOf(
                isAuroraLlm ? AURORA_LLM_LIMIT_PER_MINUTE : (userId != null ? USER_LIMIT_PER_MINUTE : ANON_LIMIT_PER_MINUTE)));
            chain.doFilter(request, response);
        } else {
            res.setStatus(429);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"rate_limit_exceeded\",\"message\":\"请求过于频繁，请稍后再试。\",\"retry_after\":60}");
            res.setHeader("Retry-After", "60");
        }
    }

    private BucketEntry createUserBucket() {
        return new BucketEntry(Bucket.builder()
            .addLimit(Bandwidth.simple(USER_LIMIT_PER_MINUTE, Duration.ofMinutes(1)))
            .addLimit(Bandwidth.builder()
                .capacity(BURST_CAPACITY)
                .refillGreedy(BURST_CAPACITY, Duration.ofMinutes(1))
                .build())
            .build());
    }

    private BucketEntry createAuroraBucket() {
        return new BucketEntry(Bucket.builder()
            .addLimit(Bandwidth.simple(AURORA_LLM_LIMIT_PER_MINUTE, Duration.ofMinutes(1)))
            .addLimit(Bandwidth.builder()
                .capacity(5)
                .refillGreedy(5, Duration.ofMinutes(1))
                .build())
            .build());
    }

    @Override
    public void init(FilterConfig filterConfig) {
        // Start background cleanup thread to evict stale buckets
        Thread cleanup = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(300_000); // Run every 5 minutes
                    evictStaleBuckets();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "rate-limit-cleanup");
        cleanup.setDaemon(true);
        cleanup.start();
    }

    private void evictStaleBuckets() {
        long threshold = System.currentTimeMillis() - 3_600_000; // 1 hour
        int evicted = 0;
        Iterator<Map.Entry<String, BucketEntry>> it = userBuckets.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, BucketEntry> entry = it.next();
            if (entry.getValue().lastAccess < threshold) {
                it.remove();
                evicted++;
            }
        }
        if (evicted > 0) {
            log.debug("Evicted {} stale rate limit buckets", evicted);
        }
    }

    @Override
    public void destroy() {
        userBuckets.clear();
    }

    private static class BucketEntry {
        final Bucket bucket;
        volatile long lastAccess;

        BucketEntry(Bucket bucket) {
            this.bucket = bucket;
            this.lastAccess = System.currentTimeMillis();
        }
    }
}
