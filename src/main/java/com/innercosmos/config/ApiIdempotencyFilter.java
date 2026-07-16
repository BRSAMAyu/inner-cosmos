package com.innercosmos.config;

import com.innercosmos.common.ApiErrorResponse;
import com.innercosmos.common.Constants;
import com.innercosmos.entity.User;
import com.innercosmos.idempotency.CachedHttpResponse;
import com.innercosmos.idempotency.IdempotencyClaim;
import com.innercosmos.idempotency.IdempotencyStore;
import com.innercosmos.idempotency.IdempotencyStoreUnavailableException;
import com.innercosmos.util.JsonUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Cross-pod idempotency boundary for externally visible v1 mutations.
 * Legacy routes remain compatible, but opt into identical semantics whenever a key is supplied.
 */
@Component
@Order(SecurityProperties.DEFAULT_FILTER_ORDER + 1)
public final class ApiIdempotencyFilter extends OncePerRequestFilter {
    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9._:-]{8,128}");
    private static final Set<String> EXCLUDED_SUFFIXES = Set.of("/stream-stage", "/rhythm-check");

    private final IdempotencyStore store;
    private final Duration ttl;
    private final int maxResponseBytes;

    public ApiIdempotencyFilter(IdempotencyStore store,
                                @Value("${inner-cosmos.idempotency.ttl:PT24H}") Duration ttl,
                                @Value("${inner-cosmos.idempotency.max-response-bytes:1048576}") int maxResponseBytes) {
        this.store = store;
        this.ttl = ttl;
        this.maxResponseBytes = maxResponseBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String path = requestPath(request);
        if (!isProtectedMutation(request.getMethod(), path)) {
            chain.doFilter(request, response);
            return;
        }

        String key = request.getHeader("Idempotency-Key");
        boolean v1 = path.startsWith("/api/v1/");
        if (key == null || key.isBlank()) {
            if (v1) writeError(response, 400, "IDEMPOTENCY_KEY_REQUIRED",
                    "Idempotency-Key is required for this v1 mutation");
            else chain.doFilter(request, response);
            return;
        }
        if (!VALID_KEY.matcher(key).matches()) {
            writeError(response, 400, "IDEMPOTENCY_KEY_INVALID",
                    "Idempotency-Key must be 8-128 URL-safe ASCII characters");
            return;
        }

        String userId = authenticatedUserId(request);
        if (userId == null) {
            writeError(response, 401, "UNAUTHORIZED", "Authentication is required before claiming an idempotency key");
            return;
        }

        CachedBodyRequest wrappedRequest = new CachedBodyRequest(request);
        String fingerprint = fingerprint(request.getMethod(), path, request.getQueryString(), wrappedRequest.body);
        String storeKey = digest(userId + "\n" + request.getMethod() + "\n" + path + "\n" + key);
        try {
            IdempotencyClaim claim = store.claim(storeKey, fingerprint, ttl);
            switch (claim.state()) {
                case REPLAY -> replay(response, claim.response());
                case CONFLICT -> writeError(response, 409, "IDEMPOTENCY_KEY_REUSED",
                        "This Idempotency-Key was already used with a different request");
                case IN_PROGRESS -> {
                    response.setHeader("Retry-After", "1");
                    writeError(response, 409, "IDEMPOTENCY_REQUEST_IN_PROGRESS",
                            "The original request is still in progress");
                }
                case ACQUIRED -> executeClaimed(wrappedRequest, response, chain, storeKey, fingerprint);
            }
        } catch (IdempotencyStoreUnavailableException unavailable) {
            response.setHeader("Retry-After", "5");
            writeError(response, 503, "IDEMPOTENCY_STORE_UNAVAILABLE",
                    "The request safety service is temporarily unavailable");
        }
    }

    private void executeClaimed(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
                                String storeKey, String fingerprint) throws IOException, ServletException {
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        boolean completed = false;
        boolean storeFailure = false;
        try {
            chain.doFilter(request, wrappedResponse);
            byte[] body = wrappedResponse.getContentAsByteArray();
            int status = wrappedResponse.getStatus();
            if (status >= 200 && status < 300 && body.length <= maxResponseBytes) {
                store.complete(storeKey, fingerprint,
                        new CachedHttpResponse(status, wrappedResponse.getContentType(), body,
                                wrappedResponse.getHeader("ETag")), ttl);
                completed = true;
            }
        } catch (IdempotencyStoreUnavailableException failure) {
            storeFailure = true;
            try { store.abort(storeKey, fingerprint); } catch (RuntimeException ignored) {}
            response.reset();
            throw failure;
        } finally {
            if (!completed && !storeFailure) store.abort(storeKey, fingerprint);
            if (!storeFailure) wrappedResponse.copyBodyToResponse();
        }
    }

    private void replay(HttpServletResponse response, CachedHttpResponse cached) throws IOException {
        response.setStatus(cached.status());
        if (cached.contentType() != null) response.setContentType(cached.contentType());
        if (cached.etag() != null) response.setHeader("ETag", cached.etag());
        response.setHeader("Idempotency-Replayed", "true");
        response.getOutputStream().write(cached.body());
    }

    private boolean isProtectedMutation(String method, String path) {
        if (!"POST".equalsIgnoreCase(method)) return false;
        String normalized = path.replaceFirst("^/api/v1/", "/api/");
        boolean core = normalized.startsWith("/api/aurora/")
                || normalized.startsWith("/api/capsule/")
                || normalized.startsWith("/api/letters/")
                || normalized.startsWith("/api/persona-chat/");
        return core && EXCLUDED_SUFFIXES.stream().noneMatch(normalized::endsWith);
    }

    private String authenticatedUserId(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof User user && user.id != null) {
            return user.id.toString();
        }
        var session = request.getSession(false);
        if (session != null) {
            Object value = session.getAttribute(Constants.SESSION_USER_KEY);
            if (value instanceof Long userId && userId > 0) return userId.toString();
        }
        return null;
    }

    private String requestPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (uri == null) return "";
        return context != null && !context.isEmpty() && uri.startsWith(context)
                ? uri.substring(context.length()) : uri;
    }

    private String fingerprint(String method, String path, String query, byte[] body) {
        MessageDigest digest = sha256();
        digest.update(method.toUpperCase().getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '\n');
        digest.update(path.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '\n');
        if (query != null) digest.update(query.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '\n');
        digest.update(body);
        return HexFormat.of().formatHex(digest.digest());
    }

    private String digest(String value) {
        return HexFormat.of().formatHex(sha256().digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }

    private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JsonUtils.toJson(ApiErrorResponse.of(code, message, status)));
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.body = request.getInputStream().readAllBytes();
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener readListener) {}
                @Override public int read() { return input.read(); }
                @Override public int read(byte[] bytes, int off, int len) { return input.read(bytes, off, len); }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(),
                    getCharacterEncoding() == null ? StandardCharsets.UTF_8
                            : java.nio.charset.Charset.forName(getCharacterEncoding())));
        }
    }
}
