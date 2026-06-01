package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.AiInteractionLog;
import com.innercosmos.mapper.AiInteractionLogMapper;
import com.innercosmos.service.AiLogService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiLogServiceImpl implements AiLogService {
    private final AiInteractionLogMapper mapper;

    public AiLogServiceImpl(AiInteractionLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void record(Long userId, String moduleName, String prompt, String response, boolean success, long latencyMs) {
        recordDetailed(userId, moduleName, "UNKNOWN", "unknown", prompt, response, null, null,
                success, false, success ? null : "AI call failed", latencyMs);
    }

    @Override
    public void recordDetailed(Long userId, String moduleName, String provider, String modelName,
                               String prompt, String response, String requestJson, String responseJson,
                               boolean success, boolean fallbackUsed, String errorMessage, long latencyMs) {
        AiInteractionLog log = new AiInteractionLog();
        log.userId = userId;
        log.moduleName = moduleName;
        log.provider = provider == null || provider.isBlank() ? "UNKNOWN" : provider;
        log.modelName = modelName == null || modelName.isBlank() ? "unknown" : modelName;
        log.requestPrompt = prompt;
        log.responseText = response;
        log.requestJson = requestJson;
        log.responseJson = responseJson;
        log.success = success;
        log.fallbackUsed = fallbackUsed;
        log.errorMessage = errorMessage;
        log.latencyMs = latencyMs;
        log.tokenInputEstimate = prompt == null ? 0 : Math.max(1, prompt.length() / 2);
        log.tokenOutputEstimate = response == null ? 0 : Math.max(1, response.length() / 2);
        mapper.insert(log);
    }

    @Override
    public List<AiInteractionLog> listRecent(Long userId) {
        return listRecent(userId, null, null, null);
    }

    @Override
    public List<AiInteractionLog> listRecent(Long userId, String moduleName, String provider, Boolean success) {
        QueryWrapper<AiInteractionLog> query = new QueryWrapper<>();
        if (userId != null) {
            query.eq("user_id", userId);
        }
        if (moduleName != null && !moduleName.isBlank()) {
            query.eq("module_name", moduleName);
        }
        if (provider != null && !provider.isBlank()) {
            query.eq("provider", provider);
        }
        if (success != null) {
            query.eq("success", success);
        }
        query.orderByDesc("id").last("LIMIT 100");
        return mapper.selectList(query);
    }

    @Override
    public AiInteractionLog latest() {
        return mapper.selectOne(new QueryWrapper<AiInteractionLog>().orderByDesc("id").last("LIMIT 1"));
    }
}
