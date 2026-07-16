package com.innercosmos.streaming;

import com.innercosmos.dto.ChatRequest;

public interface AuroraStreamStageStore {
    String stage(Long userId, ChatRequest request);

    ChatRequest consume(Long userId, String token);
}
