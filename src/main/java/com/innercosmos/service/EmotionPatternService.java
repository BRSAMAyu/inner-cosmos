package com.innercosmos.service;

import com.innercosmos.vo.EmotionPatternVO;
import java.time.LocalDate;
import java.util.List;

/**
 * Service for detecting and analyzing emotion patterns over time.
 */
public interface EmotionPatternService {
    /**
     * Detect emotion patterns for a user over the given number of days.
     */
    List<EmotionPatternVO> detectPatterns(Long userId, int days);

    /**
     * Get the most dominant pattern type for a user.
     */
    EmotionPatternVO getDominantPattern(Long userId, int days);

    /**
     * Get emotion summary for a specific date range.
     */
    EmotionPatternVO getRangeSummary(Long userId, LocalDate startDate, LocalDate endDate);
}