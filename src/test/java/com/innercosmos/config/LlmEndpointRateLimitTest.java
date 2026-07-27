package com.innercosmos.config;

import com.innercosmos.common.Constants;
import com.innercosmos.ratelimit.RateLimitDecision;
import com.innercosmos.ratelimit.RateLimitKey;
import com.innercosmos.ratelimit.RateLimitPolicy;
import com.innercosmos.ratelimit.RateLimitProperties;
import com.innercosmos.ratelimit.RateLimitStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 2026-07-27 audit (P2): every non-Aurora endpoint below also calls a real AI provider
 * synchronously in the request path, but previously fell through {@link ApiRateLimitFilter}'s
 * old {@code isAuroraLlm} predicate into the generic "user" band (40/min -- 8x more budget than
 * an LLM-backed call should get). Proves they now land in the dedicated "model-backed" band
 * instead, while leaving Aurora's own chat loop's "aurora" band and plain endpoints' "user" band
 * exactly as they were. This is a plain unit test against the filter directly (like
 * {@link ApiRateLimitFilterTest}), so it exercises the real {@link RateLimitProperties} defaults
 * without needing {@link TestRateLimitConfig} (which only neutralises rate limiting inside a full
 * Spring context, irrelevant here since nothing is bootstrapped).
 */
class LlmEndpointRateLimitTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void thoughtShredderProcessUsesModelBackedBand() throws Exception {
        assertScope("/api/thought-shredder/process", "model-backed");
    }

    @Test
    void personaChatMessageUsesModelBackedBand() throws Exception {
        assertScope("/api/persona-chat/message", "model-backed");
    }

    @Test
    void v1PrefixedPersonaChatMessageStillNormalizesToModelBackedBand() throws Exception {
        assertScope("/api/v1/persona-chat/message", "model-backed");
    }

    @Test
    void capsuleSandboxRespondWithPathVariableUsesModelBackedBand() throws Exception {
        assertScope("/api/capsule/123/sandbox/respond", "model-backed");
    }

    @Test
    void capsuleSandboxFeedbackWithPathVariableUsesModelBackedBand() throws Exception {
        assertScope("/api/capsule/456/sandbox/feedback", "model-backed");
    }

    @Test
    void capsuleGenomeRecompileWithPathVariableUsesModelBackedBand() throws Exception {
        assertScope("/api/capsule/789/genome/recompile", "model-backed");
    }

    @Test
    void todoSplitWithPathVariableUsesModelBackedBand() throws Exception {
        assertScope("/api/todos/42/split", "model-backed");
    }

    @Test
    void auroraChatKeepsItsOwnDedicatedBandUnaffectedByTheNewBand() throws Exception {
        assertScope("/api/aurora/chat", "aurora");
    }

    @Test
    void auroraDefaultPolicyAllowsARealMultiTurnDemo() {
        RateLimitPolicy policy = new RateLimitProperties().aurora();
        assertThat(policy.capacity()).isGreaterThanOrEqualTo(20);
        assertThat(policy.refillPerMinute()).isGreaterThanOrEqualTo(20);
    }

    @Test
    void ordinaryCapsuleContextEndpointStaysOnTheGenericUserBand() throws Exception {
        // Negative control: an overly broad regex could accidentally sweep other
        // /api/capsule/{id}/** routes into the model-backed band. It must not.
        assertScope("/api/capsule/123/context", "user");
    }

    @Test
    void ordinaryTodoStatusEndpointStaysOnTheGenericUserBand() throws Exception {
        assertScope("/api/todos/42/status", "user");
    }

    @Test
    void modelBackedEndpointGetsTheDedicatedPolicyCapacityNotTheGenericUserCapacity() throws Exception {
        RateLimitProperties properties = new RateLimitProperties();
        CapturingStore store = new CapturingStore();
        ApiRateLimitFilter filter = new ApiRateLimitFilter(store, properties);
        MockHttpServletRequest request = post("/api/thought-shredder/process");
        request.getSession(true).setAttribute(Constants.SESSION_USER_KEY, 7L);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(store.policies).hasSize(1);
        assertThat(store.policies.get(0)).isEqualTo(properties.modelBacked());
        assertThat(store.policies.get(0)).isNotEqualTo(properties.user());
    }

    private void assertScope(String path, String expectedScope) throws Exception {
        CapturingStore store = new CapturingStore();
        ApiRateLimitFilter filter = new ApiRateLimitFilter(store, new RateLimitProperties());
        MockHttpServletRequest request = post(path);
        request.getSession(true).setAttribute(Constants.SESSION_USER_KEY, 7L);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(store.keys).containsExactly(RateLimitKey.forSubject(expectedScope, "7"));
    }

    private MockHttpServletRequest post(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        return request;
    }

    private static final class CapturingStore implements RateLimitStore {
        private final List<String> keys = new ArrayList<>();
        private final List<RateLimitPolicy> policies = new ArrayList<>();

        @Override
        public RateLimitDecision consume(String key, RateLimitPolicy policy) {
            keys.add(key);
            policies.add(policy);
            return new RateLimitDecision(true, policy.capacity() - 1);
        }
    }
}
