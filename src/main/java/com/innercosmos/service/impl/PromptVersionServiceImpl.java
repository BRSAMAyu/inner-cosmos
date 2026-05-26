package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.PromptTemplateEntity;
import com.innercosmos.mapper.PromptTemplateMapper;
import com.innercosmos.service.PromptVersionService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PromptVersionServiceImpl implements PromptVersionService {

    private final PromptTemplateMapper mapper;

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
        // Determine the next version number
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
}
