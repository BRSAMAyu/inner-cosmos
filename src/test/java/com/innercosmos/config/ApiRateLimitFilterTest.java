package com.innercosmos.config;

import com.innercosmos.common.Constants;
import com.innercosmos.entity.User;
import com.innercosmos.ratelimit.RateLimitDecision;
import com.innercosmos.ratelimit.RateLimitKey;
import com.innercosmos.ratelimit.RateLimitProperties;
import com.innercosmos.ratelimit.RateLimitStore;
import com.innercosmos.ratelimit.RateLimitStoreUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiRateLimitFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void verifiedBearerPrincipalOverridesUnrelatedBrowserSessionForQuotaIdentity() throws Exception {
        CapturingStore store = new CapturingStore();
        ApiRateLimitFilter filter = new ApiRateLimitFilter(store, new RateLimitProperties());
        User bearerUser = new User();
        bearerUser.id = 42L;
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(bearerUser, null, List.of()));
        MockHttpServletRequest request = post("/api/todo");
        request.getSession(true).setAttribute(Constants.SESSION_USER_KEY, 99L);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(store.keys).containsExactly(RateLimitKey.forSubject("user", "42"));
    }

    @Test
    void serverOwnedSessionIdentityUsesTheSameStableUserKey() throws Exception {
        CapturingStore store = new CapturingStore();
        ApiRateLimitFilter filter = new ApiRateLimitFilter(store, new RateLimitProperties());
        MockHttpServletRequest request = post("/api/todo");
        request.getSession(true).setAttribute(Constants.SESSION_USER_KEY, 42L);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(store.keys).containsExactly(RateLimitKey.forSubject("user", "42"));
    }

    @Test
    void anonymousClientsAreIsolatedByUnspoofableRemoteAddress() throws Exception {
        CapturingStore store = new CapturingStore();
        ApiRateLimitFilter filter = new ApiRateLimitFilter(store, new RateLimitProperties());
        MockHttpServletRequest first = post("/api/todo");
        first.setRemoteAddr("192.0.2.10");
        first.addHeader("X-Forwarded-For", "attacker-controlled");
        MockHttpServletRequest second = post("/api/todo");
        second.setRemoteAddr("192.0.2.11");

        filter.doFilter(first, new MockHttpServletResponse(), new MockFilterChain());
        filter.doFilter(second, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(store.keys).containsExactly(
                RateLimitKey.forSubject("anonymous", "192.0.2.10"),
                RateLimitKey.forSubject("anonymous", "192.0.2.11"));
    }

    @Test
    void unavailableDistributedStoreReturns503InsteadOfFailingOpen() throws Exception {
        RateLimitStore store = (key, policy) -> {
            throw new RateLimitStoreUnavailableException(new IllegalStateException("down"));
        };
        ApiRateLimitFilter filter = new ApiRateLimitFilter(store, new RateLimitProperties());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(post("/api/todo"), response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("RATE_LIMIT_UNAVAILABLE");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void auroraStreamProtocolConsumesExactlyOneQuotaTokenPerTurn() throws Exception {
        CapturingStore store = new CapturingStore();
        ApiRateLimitFilter filter = new ApiRateLimitFilter(store, new RateLimitProperties());
        MockHttpServletRequest stage = post("/api/v1/aurora/stream-stage");
        stage.getSession(true).setAttribute(Constants.SESSION_USER_KEY, 42L);
        MockHttpServletRequest continuation = new MockHttpServletRequest(
                "GET", "/api/v1/aurora/stream");
        continuation.setServletPath("/api/v1/aurora/stream");
        continuation.getSession(true).setAttribute(Constants.SESSION_USER_KEY, 42L);

        filter.doFilter(stage, new MockHttpServletResponse(), new MockFilterChain());
        filter.doFilter(continuation, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(store.keys).containsExactly(RateLimitKey.forSubject("aurora", "42"));
    }

    @Test
    void unlimitedClassroomDemoBypassesLoginPublicDemoAndAuthenticatedBusinessQuotas()
            throws Exception {
        RejectingStore store = new RejectingStore();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("inner-cosmos.demo.unlimited-usage-enabled", "true");
        ApiRateLimitFilter filter = new ApiRateLimitFilter(
                store, new RateLimitProperties(), environment);

        List<MockHttpServletRequest> requests = List.of(
                post("/api/public/demo/enter/lin-che"),
                post("/api/v1/auth/login"),
                post("/api/public/demo/sandbox"),
                authenticatedPost("/api/v1/aurora/message-rich", 42L),
                authenticatedPost("/api/social/friends/request", 42L));

        for (MockHttpServletRequest request : requests) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request, response, chain);
            assertThat(chain.getRequest()).as(request.getRequestURI()).isNotNull();
            assertThat(response.getStatus()).as(request.getRequestURI()).isEqualTo(200);
        }
        assertThat(store.keys).isEmpty();
    }

    @Test
    void defaultModeStillRejectsRequestsWhenQuotaStoreDeniesThem() throws Exception {
        RejectingStore store = new RejectingStore();
        ApiRateLimitFilter filter = new ApiRateLimitFilter(store, new RateLimitProperties());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(post("/api/public/demo/enter/lin-che"), response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
        assertThat(chain.getRequest()).isNull();
        assertThat(store.keys).containsExactly(RateLimitKey.forSubject("login", "127.0.0.1"));
    }

    @Test
    void prodProfileKeepsQuotaProtectionEvenIfDemoFlagIsMisconfiguredTrue() throws Exception {
        RejectingStore store = new RejectingStore();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("inner-cosmos.demo.unlimited-usage-enabled", "true");
        environment.setActiveProfiles("prod");
        ApiRateLimitFilter filter = new ApiRateLimitFilter(
                store, new RateLimitProperties(), environment);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(post("/api/public/demo/enter/lin-che"),
                response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(store.keys).containsExactly(RateLimitKey.forSubject("login", "127.0.0.1"));
    }

    private MockHttpServletRequest post(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        return request;
    }

    private MockHttpServletRequest authenticatedPost(String path, Long userId) {
        MockHttpServletRequest request = post(path);
        request.getSession(true).setAttribute(Constants.SESSION_USER_KEY, userId);
        return request;
    }

    private static final class CapturingStore implements RateLimitStore {
        private final List<String> keys = new ArrayList<>();

        @Override
        public RateLimitDecision consume(String key, com.innercosmos.ratelimit.RateLimitPolicy policy) {
            keys.add(key);
            return new RateLimitDecision(true, policy.capacity() - 1);
        }
    }

    private static final class RejectingStore implements RateLimitStore {
        private final List<String> keys = new ArrayList<>();

        @Override
        public RateLimitDecision consume(String key, com.innercosmos.ratelimit.RateLimitPolicy policy) {
            keys.add(key);
            return new RateLimitDecision(false, 0);
        }
    }
}
