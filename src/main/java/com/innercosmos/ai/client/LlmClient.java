package com.innercosmos.ai.client;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface LlmClient {
    String chat(LlmRequest request);

    SseEmitter streamChat(LlmRequest request);
}
