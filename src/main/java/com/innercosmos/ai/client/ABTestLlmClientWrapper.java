package com.innercosmos.ai.client;

import com.innercosmos.service.AiLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Executor;

/**
 * Wrapper for LlmClient that handles A/B test forceMock logic.
 * Delegates to the actual LlmClient but checks for forceMock flag first.
 */
public class ABTestLlmClientWrapper implements LlmClient {
    private static final Logger log = LoggerFactory.getLogger(ABTestLlmClientWrapper.class);

    private final LlmClient delegate;
    private final MockLlmClient mockClient;

    public ABTestLlmClientWrapper(LlmClient delegate, AiLogService aiLogService, Executor aiExecutor) {
        this.delegate = delegate;
        this.mockClient = new MockLlmClient(aiExecutor);
    }

    @Override
    public String chat(LlmRequest request) {
        // Check if A/B test forceMock is set
        if (Boolean.TRUE.equals(request.forceMock)) {
            log.debug("A/B test: forcing MOCK mode for request {}", request.moduleName);
            return mockClient.chat(request);
        }

        return delegate.chat(request);
    }

    @Override
    public SseEmitter streamChat(LlmRequest request) {
        // Check if A/B test forceMock is set
        if (Boolean.TRUE.equals(request.forceMock)) {
            log.debug("A/B test: forcing MOCK mode for stream request {}", request.moduleName);
            return mockClient.streamChat(request);
        }

        return delegate.streamChat(request);
    }
}
