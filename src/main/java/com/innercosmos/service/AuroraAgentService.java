package com.innercosmos.service;

import com.innercosmos.dto.ChatRequest;
import com.innercosmos.vo.AuroraReplyVO;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AuroraAgentService {
    String reply(Long userId, ChatRequest request);

    AuroraReplyVO replyRich(Long userId, ChatRequest request);

    SseEmitter stream(Long userId, Long sessionId, String message);
}
