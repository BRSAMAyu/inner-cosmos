package com.innercosmos.service;

import com.innercosmos.vo.AuroraMemoryContextVO;

public interface AuroraMemoryContextService {
    AuroraMemoryContextVO buildContext(Long userId, Long sessionId, String userInput, int shortTermLimit, int longTermLimit);
}
