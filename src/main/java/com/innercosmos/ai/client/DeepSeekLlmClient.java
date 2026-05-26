package com.innercosmos.ai.client;

import com.innercosmos.exception.AiProviderException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class DeepSeekLlmClient implements LlmClient {
    @Override
    public String chat(LlmRequest request) {
        throw new AiProviderException("DeepSeek remote mode is reserved for later configuration.");
    }

    @Override
    public SseEmitter streamChat(LlmRequest request) {
        throw new AiProviderException("DeepSeek streaming mode is reserved for later configuration.");
    }
}
