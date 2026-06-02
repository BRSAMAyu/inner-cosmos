package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.RelationMention;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.RelationMentionMapper;
import com.innercosmos.service.RelationNetworkService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of RelationNetworkService with LLM-based relation extraction.
 */
@Service
public class RelationNetworkServiceImpl implements RelationNetworkService {
    private final StructuredAiService structuredAiService;
    private final RelationMentionMapper relationMentionMapper;
    private final MemoryCardMapper memoryCardMapper;

    public RelationNetworkServiceImpl(
            StructuredAiService structuredAiService,
            RelationMentionMapper relationMentionMapper,
            MemoryCardMapper memoryCardMapper) {
        this.structuredAiService = structuredAiService;
        this.relationMentionMapper = relationMentionMapper;
        this.memoryCardMapper = memoryCardMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void extractFromMemory(Long userId, Long memoryCardId) {
        MemoryCard card = memoryCardMapper.selectById(memoryCardId);
        if (card == null || !card.userId.equals(userId)) {
            return;
        }

        String prompt = buildRelationExtractionPrompt(card);

        try {
            RelationExtractionResult result = structuredAiService.call(
                userId, "RELATION_EXTRACT", prompt,
                Map.of("cardTitle", card.title, "cardSummary", card.summary),
                RelationExtractionResult.class,
                () -> fallbackRelationExtraction(card)
            );

            processRelations(userId, memoryCardId, card.sourceSessionId, result);

        } catch (Exception e) {
            // Silently fail
        }
    }

    @Override
    public List<RelationMention> findRelations(Long userId) {
        QueryWrapper<RelationMention> query = new QueryWrapper<>();
        query.eq("user_id", userId).orderByDesc("created_at");
        return relationMentionMapper.selectList(query);
    }

    @Override
    public Map<String, Integer> getRelationStats(Long userId) {
        List<RelationMention> relations = findRelations(userId);
        Map<String, Integer> stats = new HashMap<>();

        for (RelationMention r : relations) {
            stats.merge(r.relationType != null ? r.relationType : "UNKNOWN", 1, Integer::sum);
        }

        return stats;
    }

    @Override
    public List<RelationMention> findHighEmotionRelations(Long userId) {
        QueryWrapper<RelationMention> query = new QueryWrapper<>();
        query.eq("user_id", userId)
             .isNotNull("emotion_tags")
             .ne("emotion_tags", "")
             .orderByDesc("created_at");

        return relationMentionMapper.selectList(query).stream()
                .filter(r -> hasHighEmotion(r.emotionTags))
                .collect(Collectors.toList());
    }

    @Override
    public List<RelationMention.TimelinePoint> getRelationTimeline(Long userId, String relationLabel) {
        QueryWrapper<RelationMention> query = new QueryWrapper<>();
        query.eq("user_id", userId)
             .eq("relation_label", relationLabel)
             .orderByAsc("created_at");

        List<RelationMention> mentions = relationMentionMapper.selectList(query);

        return mentions.stream()
                .map(m -> new RelationMention.TimelinePoint(m.createdAt, m.emotionTags, m.triggerSummary))
                .collect(Collectors.toList());
    }

    @Override
    public double calculateHealthScore(Long userId, String relationLabel) {
        List<RelationMention> mentions = relationMentionMapper.selectList(
            new QueryWrapper<RelationMention>()
                .eq("user_id", userId)
                .eq("relation_label", relationLabel)
        );

        if (mentions.isEmpty()) return 0.5;

        // Simple health calculation based on emotion patterns
        double positiveCount = 0;
        double totalCount = mentions.size();

        for (RelationMention m : mentions) {
            if (m.emotionTags != null && m.emotionTags.contains("积极")) {
                positiveCount++;
            }
        }

        return Math.min(1.0, Math.max(0.0, positiveCount / totalCount * 1.5));
    }

    private String buildRelationExtractionPrompt(MemoryCard card) {
        return String.format("""
            分析以下记忆内容,提取其中涉及的人际关系.

            记忆标题:%s
            记忆摘要:%s

            请识别:
            1. relationLabel - 关系对象(如:妈妈、同事、朋友X)
            2. relationType - 关系类型:FAMILY(家庭)、FRIEND(朋友)、COLLEAGUE(同事)、PARTNER(伴侣)、OTHER(其他)
            3. emotionTags - 情绪标签(数组):如["支持", "压力", "温暖", "冲突"]
            4. triggerSummary - 触发摘要:简短描述发生了什么

            返回 JSON 格式:
            {
              "relations": [
                {
                  "relationLabel": "关系对象",
                  "relationType": "FRIEND",
                  "emotionTags": ["标签1", "标签2"],
                  "triggerSummary": "触发摘要"
                }
              ]
            }
            """, card.title, card.summary != null && card.summary.length() > 200 ? card.summary.substring(0, 200) + "..." : card.summary);
    }

    private void processRelations(Long userId, Long memoryCardId, Long sessionId, RelationExtractionResult result) {
        if (result.relations == null || result.relations.isEmpty()) {
            return;
        }

        for (RelationDescriptor desc : result.relations) {
            if (desc.relationLabel == null || desc.relationLabel.isBlank()) continue;

            RelationMention mention = new RelationMention();
            mention.userId = userId;
            mention.sourceSessionId = sessionId;
            mention.memoryCardId = memoryCardId;
            mention.relationLabel = desc.relationLabel;
            mention.relationType = desc.relationType != null ? desc.relationType : "OTHER";
            mention.emotionTags = desc.emotionTags != null ? desc.emotionTags.toString() : "[]";
            mention.triggerSummary = desc.triggerSummary != null ? desc.triggerSummary : "";
            mention.boundaryHint = "";

            relationMentionMapper.insert(mention);
        }
    }

    private RelationExtractionResult fallbackRelationExtraction(MemoryCard card) {
        RelationExtractionResult result = new RelationExtractionResult();
        result.relations = new ArrayList<>();

        String content = (card.summary + " " + card.title).toLowerCase();

        // Simple keyword-based extraction
        String[] relations = {"妈妈", "爸爸", "同事", "朋友", "家人", "老师"};
        for (String relation : relations) {
            if (content.contains(relation)) {
                RelationDescriptor desc = new RelationDescriptor();
                desc.relationLabel = relation;
                desc.relationType = "FAMILY";
                desc.emotionTags = List.of("未分类");
                desc.triggerSummary = "记忆中提及" + relation;
                result.relations.add(desc);
            }
        }

        return result;
    }

    private boolean hasHighEmotion(String emotionTags) {
        if (emotionTags == null) return false;
        String[] highEmotions = {"焦虑", "愤怒", "痛苦", "冲突", "压力"};
        for (String emotion : highEmotions) {
            if (emotionTags.contains(emotion)) return true;
        }
        return false;
    }

    private static class RelationExtractionResult {
        public List<RelationDescriptor> relations;
    }

    private static class RelationDescriptor {
        public String relationLabel;
        public String relationType;
        public List<String> emotionTags;
        public String triggerSummary;
    }
}
