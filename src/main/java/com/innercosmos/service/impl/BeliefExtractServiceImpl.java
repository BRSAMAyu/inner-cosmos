package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.entity.BeliefPattern;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.mapper.BeliefPatternMapper;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.service.BeliefExtractService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of BeliefExtractService using LLM for semantic belief extraction.
 */
@Service
public class BeliefExtractServiceImpl implements BeliefExtractService {
    private final StructuredAiService structuredAiService;
    private final BeliefPatternMapper beliefPatternMapper;
    private final MemoryCardMapper memoryCardMapper;

    public BeliefExtractServiceImpl(
            StructuredAiService structuredAiService,
            BeliefPatternMapper beliefPatternMapper,
            MemoryCardMapper memoryCardMapper) {
        this.structuredAiService = structuredAiService;
        this.beliefPatternMapper = beliefPatternMapper;
        this.memoryCardMapper = memoryCardMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void extractFromMemory(Long userId, Long memoryCardId) {
        MemoryCard card = memoryCardMapper.selectById(memoryCardId);
        if (card == null || !card.userId.equals(userId)) {
            return;
        }

        String prompt = buildBeliefExtractionPrompt(card);

        try {
            BeliefExtractionResult result = structuredAiService.call(
                userId, "BELIEF_EXTRACT", prompt,
                Map.of("cardTitle", card.title, "cardSummary", card.summary),
                BeliefExtractionResult.class,
                () -> fallbackBeliefExtraction(card)
            );

            processBeliefs(userId, memoryCardId, result);

        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(getClass()).error(
                    "Belief extraction failed for user {} card {}: {}", userId, memoryCardId, e.getMessage(), e);
        }
    }

    @Override
    public List<BeliefPattern> findBeliefs(Long userId) {
        QueryWrapper<BeliefPattern> query = new QueryWrapper<>();
        query.eq("user_id", userId).eq("status", "ACTIVE").orderByDesc("strength_score");
        return beliefPatternMapper.selectList(query);
    }

    @Override
    public List<BeliefPattern> findByCategory(Long userId, String category) {
        QueryWrapper<BeliefPattern> query = new QueryWrapper<>();
        query.eq("user_id", userId).eq("belief_category", category).eq("status", "ACTIVE");
        return beliefPatternMapper.selectList(query);
    }

    @Override
    public List<BeliefPattern> findStrongBeliefs(Long userId, double minStrength) {
        QueryWrapper<BeliefPattern> query = new QueryWrapper<>();
        query.eq("user_id", userId).eq("status", "ACTIVE")
             .ge("strength_score", minStrength)
             .orderByDesc("strength_score");
        return beliefPatternMapper.selectList(query);
    }

    @Override
    public List<ContradictionPair> findContradictions(Long userId) {
        List<BeliefPattern> beliefs = findBeliefs(userId);
        List<ContradictionPair> contradictions = new ArrayList<>();

        // Simple contradiction detection: opposing belief types
        for (int i = 0; i < beliefs.size(); i++) {
            for (int j = i + 1; j < beliefs.size(); j++) {
                BeliefPattern a = beliefs.get(i);
                BeliefPattern b = beliefs.get(j);
                if (areContradictory(a, b)) {
                    ContradictionPair pair = new ContradictionPair();
                    pair.beliefA = a;
                    pair.beliefB = b;
                    pair.contradictionReason = inferContradictionReason(a, b);
                    contradictions.add(pair);
                }
            }
        }

        return contradictions;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recalculateStrength(Long beliefId) {
        BeliefPattern belief = beliefPatternMapper.selectById(beliefId);
        if (belief == null) return;

        // Simple strength calculation: based on confirmation count and recency
        double baseStrength = 0.3;
        double confirmationBoost = Math.min(belief.confirmationCount * 0.1, 0.4);
        double recencyBoost = 0.0;

        if (belief.lastConfirmedAt != null) {
            long daysSince = java.time.temporal.ChronoUnit.DAYS.between(
                belief.lastConfirmedAt, LocalDateTime.now());
            recencyBoost = Math.max(0, 0.3 - (daysSince * 0.01));
        }

        belief.strengthScore = BeliefPattern.clampStrength(baseStrength + confirmationBoost + recencyBoost);
        beliefPatternMapper.updateById(belief);
    }

    private String buildBeliefExtractionPrompt(MemoryCard card) {
        return String.format("""
            分析以下记忆内容,提取其中反映的信念模式.

            记忆标题:%s
            记忆摘要:%s

            请识别:
            1. beliefs[] - 信念内容:用户可能持有的潜在信念或假设
            2. beliefType - 信念类型:SELF(关于自我)、WORLD(关于世界)、OTHERS(关于他人)、FUTURE(关于未来)
            3. beliefCategory - 信念分类:如"能力认知"、"关系模式"、"价值判断"、"期望设定"等

            对于每个信念,保持温和、非评判的语言.只提取明确反映的信念模式.

            返回 JSON 格式:
            {
              "beliefs": [
                {
                  "content": "信念内容描述",
                  "type": "SELF|WORLD|OTHERS|FUTURE",
                  "category": "分类名称"
                }
              ]
            }
            """, card.title, card.summary);
    }

    private void processBeliefs(Long userId, Long memoryCardId, BeliefExtractionResult result) {
        if (result.beliefs == null || result.beliefs.isEmpty()) {
            return;
        }

        for (BeliefDescriptor desc : result.beliefs) {
            if (desc.content == null || desc.content.isBlank()) continue;

            // Check if similar belief already exists
            QueryWrapper<BeliefPattern> query = new QueryWrapper<>();
            query.eq("user_id", userId)
                 .eq("belief_content", desc.content)
                 .eq("status", "ACTIVE");

            BeliefPattern existing = beliefPatternMapper.selectOne(query);

            if (existing != null) {
                // Update existing belief
                existing.confirmationCount++;
                existing.lastConfirmedAt = LocalDateTime.now();

                // Add to supporting memories
                Set<String> supporting = parseMemoryIds(existing.supportingMemoryIds);
                supporting.add(memoryCardId.toString());
                existing.supportingMemoryIds = String.join(",", supporting);

                beliefPatternMapper.updateById(existing);
            } else {
                // Create new belief
                BeliefPattern belief = new BeliefPattern();
                belief.userId = userId;
                belief.beliefContent = desc.content;
                belief.beliefType = desc.type != null ? desc.type : "SELF";
                belief.beliefCategory = desc.category != null ? desc.category : "未分类";
                belief.strengthScore = BeliefPattern.clampStrength(0.5);
                belief.supportingMemoryIds = memoryCardId.toString();
                belief.contradictingMemoryIds = "";
                belief.firstDetectedAt = LocalDateTime.now();
                belief.lastConfirmedAt = LocalDateTime.now();
                belief.confirmationCount = 1;
                belief.status = "ACTIVE";

                beliefPatternMapper.insert(belief);
            }
        }
    }

    private Set<String> parseMemoryIds(String ids) {
        if (ids == null || ids.isBlank()) return new HashSet<>();
        return Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private boolean areContradictory(BeliefPattern a, BeliefPattern b) {
        // Simple contradiction detection
        if (a.beliefCategory.equals(b.beliefCategory)) {
            // Same category, check for opposing content
            String contentA = a.beliefContent.toLowerCase();
            String contentB = b.beliefContent.toLowerCase();

            // Opposing indicators
            String[] opposing = {"不", "没", "无法", "不能", "不会"};
            boolean aNegated = Arrays.stream(opposing).anyMatch(contentA::contains);
            boolean bNegated = Arrays.stream(opposing).anyMatch(contentB::contains);

            return aNegated != bNegated;
        }

        // Different types can be contradictory
        if (a.beliefType.equals("SELF") && b.beliefType.equals("SELF")) {
            return true;
        }

        return false;
    }

    private String inferContradictionReason(BeliefPattern a, BeliefPattern b) {
        return "可能存在认知冲突:" + a.beliefContent + " vs " + b.beliefContent;
    }

    private BeliefExtractionResult fallbackBeliefExtraction(MemoryCard card) {
        BeliefExtractionResult result = new BeliefExtractionResult();
        result.beliefs = new ArrayList<>();

        // Simple fallback: extract basic beliefs from content
        String content = (card.summary + " " + card.title).toLowerCase();

        if (content.contains("我") && (content.contains("不行") || content.contains("不能"))) {
            BeliefDescriptor desc = new BeliefDescriptor();
            desc.content = "可能涉及自我能力或限制的认知";
            desc.type = "SELF";
            desc.category = "能力认知";
            result.beliefs.add(desc);
        }

        if (content.contains("别人") || content.contains("他们")) {
            BeliefDescriptor desc = new BeliefDescriptor();
            desc.content = "可能涉及对他人或关系的看法";
            desc.type = "OTHERS";
            desc.category = "关系模式";
            result.beliefs.add(desc);
        }

        return result;
    }

    private static class BeliefExtractionResult {
        public List<BeliefDescriptor> beliefs;
    }

    private static class BeliefDescriptor {
        public String content;
        public String type;
        public String category;
    }
}
