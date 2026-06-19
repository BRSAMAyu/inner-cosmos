package com.innercosmos.ai.client;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface LlmClient {
    /**
     * RUN-005 — shared output-token budget for a single Aurora reply across every
     * provider client. Raised from the old hard-coded 900 to give Aurora room for
     * a warm, multi-bubble companion turn (1-3 segments + the JSON wrapper) without
     * truncation — the user asked for a more generous per-response budget for a
     * better experience. Single source of truth so providers stay consistent.
     */
    int RESPONSE_MAX_TOKENS = 1600;

    String chat(LlmRequest request);

    SseEmitter streamChat(LlmRequest request);
}
