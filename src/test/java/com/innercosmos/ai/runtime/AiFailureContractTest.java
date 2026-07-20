package com.innercosmos.ai.runtime;

import com.fasterxml.jackson.core.JsonParseException;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A6 failure injection: every simulated provider failure must map to the right user-visible
 * degradation category (and stable risk flag), by exception TYPE and by message, through the cause
 * chain — with an unrecognised failure defaulting safely to PROVIDER_UNAVAILABLE.
 */
class AiFailureContractTest {

    @Test
    void classifiesTimeoutByTypeAndMessageAndNestedCause() {
        assertEquals(AiFailureContract.Category.TIMEOUT,
                AiFailureContract.classify(new TimeoutException("deadline exceeded")));
        assertEquals(AiFailureContract.Category.TIMEOUT,
                AiFailureContract.classify(new RuntimeException("upstream call timed out")));
        assertEquals(AiFailureContract.Category.TIMEOUT,
                AiFailureContract.classify(new RuntimeException("wrapper", new SocketTimeoutException("Read timed out"))));
    }

    @Test
    void classifiesRateLimiting() {
        assertEquals(AiFailureContract.Category.RATE_LIMITED,
                AiFailureContract.classify(new RuntimeException("HTTP 429 Too Many Requests")));
        assertEquals(AiFailureContract.Category.RATE_LIMITED,
                AiFailureContract.classify(new RuntimeException("provider returned rate_limit_exceeded")));
    }

    @Test
    void classifiesMalformedOutputByTypeAndMessage() {
        assertEquals(AiFailureContract.Category.MALFORMED_OUTPUT,
                AiFailureContract.classify(new RuntimeException("could not parse response")));
        assertEquals(AiFailureContract.Category.MALFORMED_OUTPUT,
                AiFailureContract.classify(new JsonParseException(null, "Unexpected end-of-input")));
    }

    @Test
    void defaultsUnknownAndNullToProviderUnavailable() {
        assertEquals(AiFailureContract.Category.PROVIDER_UNAVAILABLE,
                AiFailureContract.classify(new RuntimeException("connection reset by peer")));
        assertEquals(AiFailureContract.Category.PROVIDER_UNAVAILABLE,
                AiFailureContract.classify(new RuntimeException((String) null)));
    }

    @Test
    void everyCategoryHasAStableRiskFlagAndNonEmptyMessage() {
        assertEquals("TIMEOUT", AiFailureContract.Category.TIMEOUT.riskFlag);
        assertEquals("RATE_LIMITED", AiFailureContract.Category.RATE_LIMITED.riskFlag);
        assertEquals("PARSE_ERROR", AiFailureContract.Category.MALFORMED_OUTPUT.riskFlag);
        assertEquals("NETWORK_ERROR", AiFailureContract.Category.PROVIDER_UNAVAILABLE.riskFlag);
        for (AiFailureContract.Category category : AiFailureContract.Category.values()) {
            assertNotNull(category.defaultUserMessage);
            assertEquals(false, category.defaultUserMessage.isBlank());
        }
    }

    @Test
    void doesNotInfiniteLoopOnSelfReferencingCause() {
        RuntimeException loop = new RuntimeException("connection reset") {
            @Override public synchronized Throwable getCause() { return this; }
        };
        assertEquals(AiFailureContract.Category.PROVIDER_UNAVAILABLE, AiFailureContract.classify(loop));
    }
}
