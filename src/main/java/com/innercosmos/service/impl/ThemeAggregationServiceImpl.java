package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.entity.MemoryTheme;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.mapper.MemoryThemeMapper;
import com.innercosmos.service.ThemeAggregationService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ThemeAggregationServiceImpl implements ThemeAggregationService {
    private final MemoryThemeMapper memoryThemeMapper;
    private final MemoryCardMapper memoryCardMapper;

    public ThemeAggregationServiceImpl(MemoryThemeMapper memoryThemeMapper, MemoryCardMapper memoryCardMapper) {
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
    public void aggregateThemes(Long userId) {
        // Read all active MemoryCards
        QueryWrapper<MemoryCard> cardQuery = new QueryWrapper<>();
        cardQuery.eq("user_id", userId).eq("status", "ACTIVE").orderByDesc("id");
        List<MemoryCard> cards = memoryCardMapper.selectList(cardQuery);

        if (cards.isEmpty()) {
            return;
        }

        // Group by keyword similarity
        Map<String, List<MemoryCard>> themeGroups = new LinkedHashMap<>();
        for (MemoryCard card : cards) {
            String themeKey = extractThemeKey(card);
            themeGroups.computeIfAbsent(themeKey, k -> new ArrayList<>()).add(card);
        }

        // Create or update MemoryTheme records
        for (Map.Entry<String, List<MemoryCard>> entry : themeGroups.entrySet()) {
            String themeName = entry.getKey();
            List<MemoryCard> groupCards = entry.getValue();

            // Check if theme already exists
            QueryWrapper<MemoryTheme> themeQuery = new QueryWrapper<>();
            themeQuery.eq("user_id", userId).eq("theme_name", themeName);
            MemoryTheme existing = memoryThemeMapper.selectOne(themeQuery);

            if (existing != null) {
                // Update existing theme
                existing.memoryCount = groupCards.size();
                existing.averageGravity = calculateAverageGravity(groupCards);
                existing.lastTouchedAt = LocalDateTime.now();
                existing.themeSummary = buildThemeSummary(themeName, groupCards);
                existing.keywords = collectKeywords(groupCards);
                memoryThemeMapper.updateById(existing);
            } else {
                // Create new theme
                MemoryTheme theme = new MemoryTheme();
                theme.userId = userId;
                theme.themeName = themeName;
                theme.themeSummary = buildThemeSummary(themeName, groupCards);
                theme.themeType = inferThemeType(themeName);
                theme.keywords = collectKeywords(groupCards);
                theme.memoryCount = groupCards.size();
                theme.averageGravity = calculateAverageGravity(groupCards);
                theme.lastTouchedAt = LocalDateTime.now();
                theme.status = "ACTIVE";
                memoryThemeMapper.insert(theme);
            }
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
        return themeName + "：共 " + count + " 条记忆，最近一条「" + latestTitle + "」";
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
}
