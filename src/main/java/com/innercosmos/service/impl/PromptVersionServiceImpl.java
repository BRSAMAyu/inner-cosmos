package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.PromptTemplateEntity;
import com.innercosmos.mapper.PromptTemplateMapper;
import com.innercosmos.service.PromptVersionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PromptVersionServiceImpl implements PromptVersionService {

    private final PromptTemplateMapper mapper;
    private final Map<String, Map<Integer, PromptMetrics>> metricsStore = new ConcurrentHashMap<>();

    public PromptVersionServiceImpl(PromptTemplateMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String getActivePrompt(String promptKey) {
        QueryWrapper<PromptTemplateEntity> query = new QueryWrapper<>();
        query.eq("prompt_key", promptKey)
                .eq("enabled", true)
                .orderByDesc("version")
                .last("LIMIT 1");
        PromptTemplateEntity entity = mapper.selectOne(query);
        return entity != null ? entity.content : null;
    }

    @Override
    public PromptTemplateEntity createPrompt(String promptKey, String content, String description) {
        QueryWrapper<PromptTemplateEntity> query = new QueryWrapper<>();
        query.eq("prompt_key", promptKey)
                .orderByDesc("version")
                .last("LIMIT 1");
        PromptTemplateEntity latest = mapper.selectOne(query);
        int nextVersion = (latest != null && latest.version != null) ? latest.version + 1 : 1;

        PromptTemplateEntity entity = new PromptTemplateEntity();
        entity.promptKey = promptKey;
        entity.version = nextVersion;
        entity.content = content;
        entity.description = description;
        entity.enabled = true;
        mapper.insert(entity);
        return entity;
    }

    @Override
    public List<PromptTemplateEntity> listVersions(String promptKey) {
        QueryWrapper<PromptTemplateEntity> query = new QueryWrapper<>();
        query.eq("prompt_key", promptKey)
                .orderByDesc("version");
        return mapper.selectList(query);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PromptTemplateEntity rollbackToVersion(String promptKey, int version) {
        QueryWrapper<PromptTemplateEntity> query = new QueryWrapper<>();
        query.eq("prompt_key", promptKey).eq("version", version);
        PromptTemplateEntity target = mapper.selectOne(query);

        if (target == null) {
            throw new IllegalArgumentException("Version not found: " + version);
        }

        // Disable all versions
        mapper.update(null, new QueryWrapper<PromptTemplateEntity>()
                .eq("prompt_key", promptKey)
                .set("enabled", false));

        // Enable target version
        target.enabled = true;
        mapper.updateById(target);

        return target;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleVersion(Long promptId, boolean enabled) {
        PromptTemplateEntity entity = mapper.selectById(promptId);
        if (entity != null) {
            entity.enabled = enabled;
            mapper.updateById(entity);
        }
    }

    @Override
    public PromptTemplateEntity getPromptVariant(String promptKey, String variant) {
        QueryWrapper<PromptTemplateEntity> query = new QueryWrapper<>();
        query.eq("prompt_key", promptKey)
                .eq("description", variant)
                .eq("enabled", true);
        return mapper.selectOne(query);
    }

    @Override
    public void recordMetrics(String promptKey, int version, double successRate, double avgLatency) {
        metricsStore.computeIfAbsent(promptKey, k -> new ConcurrentHashMap<>())
                .compute(version, (v, existing) -> {
                    if (existing == null) {
                        return new PromptMetrics(version, successRate, avgLatency, 1);
                    }
                    // Exponential moving average
                    double alpha = 0.2;
                    return new PromptMetrics(
                            version,
                            alpha * successRate + (1 - alpha) * existing.successRate,
                            alpha * avgLatency + (1 - alpha) * existing.avgLatency,
                            existing.usageCount + 1
                    );
                });
    }

    @Override
    public Map<Integer, PromptMetrics> getPerformanceMetrics(String promptKey) {
        return metricsStore.getOrDefault(promptKey, Collections.emptyMap());
    }

    @Override
    public List<PromptTemplateEntity> findLowPerformingPrompts(double threshold) {
        List<PromptTemplateEntity> lowPerforming = new ArrayList<>();

        for (Map.Entry<String, Map<Integer, PromptMetrics>> entry : metricsStore.entrySet()) {
            for (PromptMetrics metrics : entry.getValue().values()) {
                if (metrics.successRate < threshold) {
                    QueryWrapper<PromptTemplateEntity> query = new QueryWrapper<>();
                    query.eq("prompt_key", entry.getKey()).eq("version", metrics.version);
                    PromptTemplateEntity entity = mapper.selectOne(query);
                    if (entity != null) {
                        lowPerforming.add(entity);
                    }
                }
            }
        }

        return lowPerforming;
    }
}
