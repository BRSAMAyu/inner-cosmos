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
        AiInteractionLog log = new AiInteractionLog();
        log.userId = userId;
        log.moduleName = moduleName;
        log.provider = "MOCK";
        log.modelName = "mock-inner-cosmos";
        log.requestPrompt = prompt;
        log.responseText = response;
        log.success = success;
        log.latencyMs = latencyMs;
        log.tokenInputEstimate = prompt == null ? 0 : Math.max(1, prompt.length() / 2);
        log.tokenOutputEstimate = response == null ? 0 : Math.max(1, response.length() / 2);
        mapper.insert(log);
    }

    @Override
    public List<AiInteractionLog> listRecent(Long userId) {
        QueryWrapper<AiInteractionLog> query = new QueryWrapper<>();
        if (userId != null) {
            query.eq("user_id", userId);
        }
        query.orderByDesc("id").last("LIMIT 100");
        return mapper.selectList(query);
    }
}
