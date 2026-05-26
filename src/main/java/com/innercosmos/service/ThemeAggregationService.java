package com.innercosmos.service;

import com.innercosmos.entity.MemoryTheme;

import java.util.List;

public interface ThemeAggregationService {
    List<MemoryTheme> findThemes(Long userId);

    void aggregateThemes(Long userId);

    MemoryTheme findOrCreateTheme(Long userId, String themeName, String themeType);

    List<MemoryTheme> themesForCard(Long cardId);
}
