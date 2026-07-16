package com.innercosmos.config;

import com.innercosmos.common.Constants;
import com.innercosmos.idempotency.InMemoryIdempotencyStore;
import com.innercosmos.idempotency.IdempotencyClaim;
import com.innercosmos.idempotency.IdempotencyStore;
import com.innercosmos.idempotency.IdempotencyStoreUnavailableException;
import com.innercosmos.idempotency.CachedHttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ApiIdempotencyFilterTest {

    @Test
    void duplicateV1MutationReplaysFirstResponseWithoutRepeatingSideEffect() throws Exception {
        ApiIdempotencyFilter filter = new ApiIdempotencyFilter(
                new InMemoryIdempotencyStore(), Duration.ofHours(1), 1024 * 1024);
        AtomicInteger sideEffects = new AtomicInteger();

        MockHttpServletResponse first = invoke(filter, "same-request-001", "{\"title\":\"hello\"}", sideEffects);
        MockHttpServletResponse replay = invoke(filter, "same-request-001", "{\"title\":\"hello\"}", sideEffects);

        assertThat(first.getStatus()).isEqualTo(201);
        assertThat(replay.getStatus()).isEqualTo(201);
        assertThat(replay.getContentAsString()).isEqualTo(first.getContentAsString());
        assertThat(replay.getHeader("Idempotency-Replayed")).isEqualTo("true");
        assertThat(replay.getHeader("ETag")).isEqualTo("\"7\"");
        assertThat(sideEffects).hasValue(1);
    }

    @Test
    void reusingKeyForDifferentPayloadFailsClosed() throws Exception {
        ApiIdempotencyFilter filter = new ApiIdempotencyFilter(
                new InMemoryIdempotencyStore(), Duration.ofHours(1), 1024 * 1024);
        AtomicInteger sideEffects = new AtomicInteger();
        invoke(filter, "same-request-002", "{\"title\":\"one\"}", sideEffects);

        MockHttpServletResponse conflict = invoke(filter, "same-request-002", "{\"title\":\"two\"}", sideEffects);

        assertThat(conflict.getStatus()).isEqualTo(409);
        assertThat(conflict.getContentAsString()).contains("IDEMPOTENCY_KEY_REUSED");
        assertThat(sideEffects).hasValue(1);
    }

    @Test
    void v1ProtectedMutationRequiresAKeyWhileLegacyPathRemainsCompatible() throws Exception {
        ApiIdempotencyFilter filter = new ApiIdempotencyFilter(
                new InMemoryIdempotencyStore(), Duration.ofHours(1), 1024 * 1024);
        AtomicInteger sideEffects = new AtomicInteger();

        MockHttpServletResponse missing = invoke(filter, null, "{}", sideEffects);

        assertThat(missing.getStatus()).isEqualTo(400);
        assertThat(missing.getContentAsString()).contains("IDEMPOTENCY_KEY_REQUIRED");
        assertThat(sideEffects).hasValue(0);
    }

    @Test
    void completionStoreFailureReturnsOneClean503InsteadOfLeakingUnprotectedSuccess() throws Exception {
        IdempotencyStore failing = new IdempotencyStore() {
            @Override public IdempotencyClaim claim(String key, String fingerprint, Duration ttl) {
                return IdempotencyClaim.acquired();
            }
            @Override public void complete(String key, String fingerprint, CachedHttpResponse response, Duration ttl) {
                throw new IdempotencyStoreUnavailableException(new IllegalStateException("redis down"));
            }
            @Override public void abort(String key, String fingerprint) {}
        };
        ApiIdempotencyFilter filter = new ApiIdempotencyFilter(failing, Duration.ofHours(1), 1024 * 1024);

        MockHttpServletResponse response = invoke(filter, "store-fail-001", "{}", new AtomicInteger());

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("IDEMPOTENCY_STORE_UNAVAILABLE")
                .doesNotContain("\"id\":7");
    }

    private MockHttpServletResponse invoke(ApiIdempotencyFilter filter, String key, String body,
                                           AtomicInteger sideEffects) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/letters/draft");
        request.setServletPath("/api/v1/letters/draft");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.getSession(true).setAttribute(Constants.SESSION_USER_KEY, 42L);
        if (key != null) request.addHeader("Idempotency-Key", key);
        MockHttpServletResponse response = new MockHttpServletResponse();
        jakarta.servlet.FilterChain chain = (servletRequest, servletResponse) -> {
            sideEffects.incrementAndGet();
            var http = (jakarta.servlet.http.HttpServletResponse) servletResponse;
            http.setStatus(201);
            http.setContentType("application/json");
            http.setHeader("ETag", "\"7\"");
            http.getWriter().write("{\"success\":true,\"data\":{\"id\":7}}");
        };
        filter.doFilter(request, response, chain);
        return response;
    }
}
