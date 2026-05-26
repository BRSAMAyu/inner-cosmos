package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.service.GravityService;
import com.innercosmos.service.ThoughtShredderService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ThoughtShredderServiceImpl implements ThoughtShredderService {
    private final MemoryCardMapper memoryCardMapper;
    private final GravityService gravityService;

    public ThoughtShredderServiceImpl(MemoryCardMapper memoryCardMapper, GravityService gravityService) {
        this.memoryCardMapper = memoryCardMapper;
        this.gravityService = gravityService;
    }

    @Override
    public MemoryCard process(Long userId, String rawText) {
        MemoryCard card = new MemoryCard();
        card.userId = userId;
        card.title = "思维碎纸机沉淀";
        card.summary = rawText == null ? "一次混乱输入被整理为可观察片段。" : rawText.substring(0, Math.min(rawText.length(), 100));
        card.memoryType = "SHREDDER";
        card.emotionTags = "[\"mixed\"]";
        card.keywordTags = "[\"thought-shredder\"]";
        card.peopleTags = "[]";
        card.intensityScore = 5.0;
        card.recurrenceCount = 1;
        card.userImportance = 3.0;
        card.triggerCount = 1;
        card.emotionalGravity = gravityService.calculateGravity(5, 1, 3, 1, 0);
        card.lastTouchedAt = LocalDateTime.now();
        card.visibilityLevel = "PRIVATE";
        card.status = "ACTIVE";
        memoryCardMapper.insert(card);
        return card;
    }

    @Override
    public List<MemoryCard> history(Long userId) {
        QueryWrapper<MemoryCard> query = new QueryWrapper<>();
        query.eq("user_id", userId).eq("memory_type", "SHREDDER").orderByDesc("id");
        return memoryCardMapper.selectList(query);
    }

    @Override
    public void settle(Long userId, Long memoryCardId) {
        MemoryCard card = memoryCardMapper.selectById(memoryCardId);
        if (card == null || !userId.equals(card.userId)) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.UNAUTHORIZED, "无权操作此记忆卡");
        }
        card.status = "ACTIVE";
        memoryCardMapper.updateById(card);
    }

    @Override
    public void delete(Long userId, Long memoryCardId) {
        MemoryCard card = memoryCardMapper.selectById(memoryCardId);
        if (card == null || !userId.equals(card.userId)) {
            throw new com.innercosmos.exception.BusinessException(
                    com.innercosmos.common.ErrorCode.UNAUTHORIZED, "无权操作此记忆卡");
        }
        memoryCardMapper.deleteById(memoryCardId);
    }
}
