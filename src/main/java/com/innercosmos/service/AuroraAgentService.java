package com.innercosmos.service;

import com.innercosmos.dto.ChatRequest;
import com.innercosmos.vo.AuroraReplyVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AuroraAgentService {
    String reply(Long userId, ChatRequest request);

    AuroraReplyVO replyRich(Long userId, ChatRequest request);

    SseEmitter stream(Long userId, Long sessionId, String message, String mode);

    /**
     * VS-003b — stream with an optional rich context (voice/weather/location/
     * timezone) staged by the frontend, so the SSE meta event carries the same
     * perception metadata the POST path returns.
     */
    SseEmitter stream(Long userId, Long sessionId, String message, String mode, ChatRequest richContext);

    /** VS-003b — stage rich SSE context for a soon-to-open GET /stream. */
    String stageStreamContext(ChatRequest request);

    /** VS-003b — consume (once) the staged rich context for a stream token. */
    ChatRequest consumeStage(String token);

    AuroraReplyVO generateGreeting(Long userId, Long sessionId, String mode);
}
