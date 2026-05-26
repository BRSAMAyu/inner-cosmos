package com.innercosmos.service;

import com.innercosmos.entity.AiInteractionLog;
import java.util.List;

public interface AiLogService {
    void record(Long userId, String moduleName, String prompt, String response, boolean success, long latencyMs);

    List<AiInteractionLog> listRecent(Long userId);
}
