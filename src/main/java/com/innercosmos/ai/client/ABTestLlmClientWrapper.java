package com.innercosmos.ai.client;

import com.innercosmos.service.AiLogService;
import com.innercosmos.util.DataMaskingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
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
        redact(request); // M-006: mask PII (phone/email) before egress + provider-side logging
        // Check if A/B test forceMock is set
        if (Boolean.TRUE.equals(request.forceMock)) {
            log.debug("A/B test: forcing MOCK mode for request {}", request.moduleName);
            return mockClient.chat(request);
        }

        return delegate.chat(request);
    }

    /**
     * M-006: mask obvious PII (long digit runs like phone/ID numbers, and email addresses) in
     * free-text prompt fields before they are egressed to third-party LLM providers or stored
     * in the AI interaction log. The user's emotional content is preserved — only contact
     * identifiers that Aurora does not need are redacted.
     */
    static void redact(LlmRequest request) {
        if (request.prompt != null) {
            request.prompt = DataMaskingUtils.maskContact(request.prompt);
        }
        if (request.requestJson != null) {
            // Phase-5: the structured/context path (requestJson) carries the richest PII and is
            // logged to tb_ai_interaction_log by the providers — mask it too.
            request.requestJson = DataMaskingUtils.maskContact(request.requestJson);
        }
        if (request.recentMessages != null) {
            List<String> masked = new ArrayList<>(request.recentMessages.size());
            for (String m : request.recentMessages) {
                masked.add(DataMaskingUtils.maskContact(m));
            }
            request.recentMessages = masked;
        }
    }

    @Override
    public SseEmitter streamChat(LlmRequest request) {
        redact(request); // M-006
        // Check if A/B test forceMock is set
        if (Boolean.TRUE.equals(request.forceMock)) {
            log.debug("A/B test: forcing MOCK mode for stream request {}", request.moduleName);
            return mockClient.streamChat(request);
        }

        return delegate.streamChat(request);
    }
}
