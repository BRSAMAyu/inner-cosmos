package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.structured.StructuredAiService;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.MemoryTheme;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.MemoryThemeMapper;
import com.innercosmos.service.ThemeAggregationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ThemeAggregationServiceImpl implements ThemeAggregationService {
    private final StructuredAiService structuredAiService;
    private final MemoryThemeMapper memoryThemeMapper;
    private final MemoryCardMapper memoryCardMapper;

    public ThemeAggregationServiceImpl(
            StructuredAiService structuredAiService,
            MemoryThemeMapper memoryThemeMapper,
            MemoryCardMapper memoryCardMapper) {
        this.structuredAiService = structuredAiService;
        this.memoryThemeMapper = memoryThemeMapper;
        this.memoryCardMapper = memoryCardMapper;
    }

    @Override
    public List<MemoryTheme> findThemes(Long userId) {
        QueryWrapper<MemoryTheme> query = new QueryWrapper<>();
        query.eq("user_id", userId).eq("status", "ACTIVE").orderByDesc("average_gravity");
        return memoryThemeMapper.selectList(query);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void aggregateThemes(Long userId) {
        QueryWrapper<MemoryCard> cardQuery = new QueryWrapper<>();
        cardQuery.eq("user_id", userId).eq("status", "ACTIVE").orderByDesc("id");
        List<MemoryCard> cards = memoryCardMapper.selectList(cardQuery);

        if (cards.isEmpty()) {
            return;
        }

        // Use LLM-based semantic clustering
        String prompt = buildClusteringPrompt(cards);

        try {
            ThemeClusteringResult result = structuredAiService.call(
                userId, "THEME_CLUSTER", prompt,
                Map.of("cardCount", cards.size()),
                ThemeClusteringResult.class,
                () -> fallbackClustering(cards)
            );

            processThemes(userId, result, cards);

        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(getClass()).error(
                    "Theme clustering failed for user {}, using fallback: {}", userId, e.getMessage(), e);
            ThemeClusteringResult result = fallbackClustering(cards);
            processThemes(userId, result, cards);
        }
    }

    private void processThemes(Long userId, ThemeClusteringResult result, List<MemoryCard> allCards) {
        if (result.themes == null) return;

        for (ThemeCluster themeCluster : result.themes) {
            if (themeCluster.name == null || themeCluster.name.isBlank()) continue;

            // Find cards for this theme
            List<MemoryCard> themeCards = findCardsForTheme(themeCluster, allCards);

            if (themeCards.isEmpty()) continue;

            // Check if theme already exists
            QueryWrapper<MemoryTheme> themeQuery = new QueryWrapper<>();
            themeQuery.eq("user_id", userId).eq("theme_name", themeCluster.name);
            MemoryTheme existing = memoryThemeMapper.selectOne(themeQuery);

            if (existing != null) {
                existing.memoryCount = themeCards.size();
                existing.averageGravity = calculateAverageGravity(themeCards);
                existing.lastTouchedAt = LocalDateTime.now();
                existing.themeSummary = themeCluster.summary != null ? themeCluster.summary : buildThemeSummary(themeCluster.name, themeCards);
                existing.themeType = themeCluster.type != null ? themeCluster.type : inferThemeType(themeCluster.name);
                existing.keywords = themeCluster.keywords != null ? themeCluster.keywords.toString() : collectKeywords(themeCards);
                memoryThemeMapper.updateById(existing);
            } else {
                MemoryTheme theme = new MemoryTheme();
                theme.userId = userId;
                theme.themeName = themeCluster.name;
                theme.themeSummary = themeCluster.summary != null ? themeCluster.summary : buildThemeSummary(themeCluster.name, themeCards);
                theme.themeType = themeCluster.type != null ? themeCluster.type : inferThemeType(themeCluster.name);
                theme.keywords = themeCluster.keywords != null ? themeCluster.keywords.toString() : collectKeywords(themeCards);
                theme.memoryCount = themeCards.size();
                theme.averageGravity = calculateAverageGravity(themeCards);
                theme.lastTouchedAt = LocalDateTime.now();
                theme.status = "ACTIVE";
                memoryThemeMapper.insert(theme);
            }
        }
    }

    private List<MemoryCard> findCardsForTheme(ThemeCluster themeCluster, List<MemoryCard> allCards) {
        // If theme has card indices, use them
        if (themeCluster.cardIndices != null && !themeCluster.cardIndices.isEmpty()) {
            List<MemoryCard> matched = new ArrayList<>();
            for (Integer idx : themeCluster.cardIndices) {
                if (idx >= 0 && idx < allCards.size()) {
                    matched.add(allCards.get(idx));
                }
            }
            return matched;
        }

        // Fallback: use keyword matching
        List<MemoryCard> matched = new ArrayList<>();
        if (themeCluster.keywords != null) {
            for (MemoryCard card : allCards) {
                for (String keyword : themeCluster.keywords) {
                    if ((card.title != null && card.title.contains(keyword)) || (card.summary != null && card.summary.contains(keyword))) {
                        matched.add(card);
                        break;
                    }
                }
            }
        }

        return matched.isEmpty() ? allCards : matched;
    }

    private String buildClusteringPrompt(List<MemoryCard> cards) {
        StringBuilder sb = new StringBuilder();
        sb.append("分析以下记忆内容,将它们归纳为3-6个主题.\n\n");

        int shown = Math.min(15, cards.size());
        for (int i = 0; i < shown; i++) {
            MemoryCard card = cards.get(i);
            sb.append(String.format("[%d] %s: %s\n", i, card.title,
                    card.summary != null && card.summary.length() > 50 ? card.summary.substring(0, 50) + "..." : card.summary));
        }

        sb.append("\n请识别主题并返回:\n");
        sb.append("1. name - 主题名称(简洁,2-4字)\n");
        sb.append("2. type - 主题类型:EMOTION, RELATION, WORK, GROWTH, DAILY\n");
        sb.append("3. summary - 主题摘要(一句话描述)\n");
        sb.append("4. keywords - 相关关键词数组\n");
        sb.append("5. cardIndices - 属于该主题的卡片索引数组\n");

        sb.append("\n返回 JSON 格式:\n");
        sb.append("{\n");
        sb.append("  \"themes\": [\n");
        sb.append("    {\n");
        sb.append("      \"name\": \"主题名称\",\n");
        sb.append("      \"type\": \"EMOTION\",\n");
        sb.append("      \"summary\": \"主题摘要\",\n");
        sb.append("      \"keywords\": [\"关键词1\", \"关键词2\"],\n");
        sb.append("      \"cardIndices\": [0, 3, 5]\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}");

        return sb.toString();
    }

    private ThemeClusteringResult fallbackClustering(List<MemoryCard> cards) {
        ThemeClusteringResult result = new ThemeClusteringResult();
        result.themes = new ArrayList<>();

        // Simple fallback: group by memoryType
        Map<String, List<MemoryCard>> groups = new LinkedHashMap<>();
        for (MemoryCard card : cards) {
            String type = card.memoryType != null ? card.memoryType : "DAILY";
            groups.computeIfAbsent(type, k -> new ArrayList<>()).add(card);
        }

        int index = 0;
        for (Map.Entry<String, List<MemoryCard>> entry : groups.entrySet()) {
            ThemeCluster cluster = new ThemeCluster();
            cluster.name = getThemeNameByType(entry.getKey());
            cluster.type = entry.getKey();
            cluster.summary = "与" + cluster.name + "相关的记忆";
            cluster.keywords = List.of(entry.getKey().toLowerCase());
            cluster.cardIndices = new ArrayList<>();

            for (MemoryCard card : entry.getValue()) {
                cluster.cardIndices.add(index++);
            }

            result.themes.add(cluster);
        }

        return result;
    }

    private String getThemeNameByType(String type) {
        switch (type) {
            case "EMOTION": return "情绪";
            case "RELATION": return "关系";
            case "COGNITION": return "思考";
            case "TODO": return "任务";
            default: return "日常";
        }
    }

    @Override
    public MemoryTheme findOrCreateTheme(Long userId, String themeName, String themeType) {
        QueryWrapper<MemoryTheme> query = new QueryWrapper<>();
        query.eq("user_id", userId).eq("theme_name", themeName);
        MemoryTheme theme = memoryThemeMapper.selectOne(query);

        if (theme != null) {
            return theme;
        }

        theme = new MemoryTheme();
        theme.userId = userId;
        theme.themeName = themeName;
        theme.themeSummary = themeName + "相关记忆的聚合";
        theme.themeType = themeType == null ? "GENERAL" : themeType;
        theme.keywords = "[]";
        theme.memoryCount = 0;
        theme.averageGravity = 0.0;
        theme.lastTouchedAt = LocalDateTime.now();
        theme.status = "ACTIVE";
        memoryThemeMapper.insert(theme);
        return theme;
    }

    @Override
    public List<MemoryTheme> themesForCard(Long cardId) {
        MemoryCard card = memoryCardMapper.selectById(cardId);
        if (card == null) {
            return List.of();
        }
        String themeKey = extractThemeKey(card);
        QueryWrapper<MemoryTheme> query = new QueryWrapper<>();
        query.eq("user_id", card.userId).eq("theme_name", themeKey).eq("status", "ACTIVE");
        return memoryThemeMapper.selectList(query);
    }

    private String extractThemeKey(MemoryCard card) {
        if (card.memoryType == null) return "未分类";
        switch (card.memoryType) {
            case "TODO":
                return "任务与待办";
            case "RELATION":
                return "关系与社交";
            case "COGNITION":
                return "思考与认知";
            case "EMOTION":
                return "情绪感受";
            default:
                return "日常";
        }
    }

    private Double calculateAverageGravity(List<MemoryCard> cards) {
        if (cards.isEmpty()) return 0.0;
        double sum = 0;
        for (MemoryCard card : cards) {
            sum += card.emotionalGravity == null ? 0 : card.emotionalGravity;
        }
        return Math.round(sum / cards.size() * 100.0) / 100.0;
    }

    private String buildThemeSummary(String themeName, List<MemoryCard> cards) {
        int count = cards.size();
        String latestTitle = cards.get(0).title;
        return themeName + ":共 " + count + " 条记忆,最近一条「" + latestTitle + "」";
    }

    private String collectKeywords(List<MemoryCard> cards) {
        Set<String> keywords = new LinkedHashSet<>();
        for (MemoryCard card : cards) {
            if (card.keywordTags != null && !card.keywordTags.isEmpty()) {
                String tags = card.keywordTags.replaceAll("[\\[\\]\"]", "");
                for (String tag : tags.split(",")) {
                    String trimmed = tag.trim();
                    if (!trimmed.isEmpty()) {
                        keywords.add(trimmed);
                    }
                }
            }
        }
        return keywords.stream().limit(10).collect(Collectors.toList()).toString();
    }

    private String inferThemeType(String themeName) {
        if (themeName.contains("任务") || themeName.contains("待办")) return "TASK";
        if (themeName.contains("关系") || themeName.contains("社交")) return "RELATION";
        if (themeName.contains("思考") || themeName.contains("认知")) return "COGNITION";
        if (themeName.contains("情绪") || themeName.contains("感受")) return "EMOTION";
        return "GENERAL";
    }

    private static class ThemeClusteringResult {
        public List<ThemeCluster> themes;
    }

    private static class ThemeCluster {
        public String name;
        public String type;
        public String summary;
        public List<String> keywords;
        public List<Integer> cardIndices;
    }
}
