package com.innercosmos.service;

import com.innercosmos.entity.AiInteractionLog;
import java.util.List;

public interface AiLogService {
    void record(Long userId, String moduleName, String prompt, String response, boolean success, long latencyMs);

    void recordDetailed(Long userId, String moduleName, String provider, String modelName,
                        String prompt, String response, String requestJson, String responseJson,
                        boolean success, boolean fallbackUsed, String errorMessage, long latencyMs);

    List<AiInteractionLog> listRecent(Long userId);

    List<AiInteractionLog> listRecent(Long userId, String moduleName, String provider, Boolean success);

    AiInteractionLog latest(Long userId);
}
