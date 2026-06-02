package com.innercosmos.service;

import java.util.Map;

/**
 * Service for estimating token usage and managing LLM cost control.
 * Provides token counting, cost estimation, and budget management.
 */
public interface TokenEstimationService {
    /**
     * Estimate token count for a text string.
     */
    int estimateTokens(String text);

    /**
     * Calculate total tokens for a prompt including system messages.
     */
    int calculatePromptTokens(String systemPrompt, String userPrompt);

    /**
     * Estimate response tokens based on prompt complexity.
     */
    int estimateResponseTokens(String prompt, String mode);

    /**
     * Get daily usage statistics for a user.
     */
    TokenUsageStats getDailyUsage(Long userId);

    /**
     * Check if user is within budget limits.
     */
    boolean isWithinBudget(Long userId);

    /**
     * Record actual token usage after LLM call.
     */
    void recordUsage(Long userId, String mode, int promptTokens, int responseTokens);

    /**
     * Get cost estimate for a given token count.
     */
    double estimateCost(int totalTokens, String model);

    /**
     * Get usage forecast for remaining month.
     */
    UsageForecast getForecast(Long userId);

    /**
     * Data class for usage statistics.
     */
    class TokenUsageStats {
        public int totalTokens;
        public int promptTokens;
        public int responseTokens;
        public double estimatedCost;
        public int requestCount;

        public TokenUsageStats(int totalTokens, int promptTokens, int responseTokens, double estimatedCost, int requestCount) {
            this.totalTokens = totalTokens;
            this.promptTokens = promptTokens;
            this.responseTokens = responseTokens;
            this.estimatedCost = estimatedCost;
            this.requestCount = requestCount;
        }
    }

    /**
     * Data class for usage forecast.
     */
    class UsageForecast {
        public int projectedDailyTokens;
        public int remainingDaysInMonth;
        public int projectedMonthlyTotal;
        public boolean withinBudget;

        public UsageForecast(int projectedDailyTokens, int remainingDaysInMonth, int projectedMonthlyTotal, boolean withinBudget) {
            this.projectedDailyTokens = projectedDailyTokens;
            this.remainingDaysInMonth = remainingDaysInMonth;
            this.projectedMonthlyTotal = projectedMonthlyTotal;
            this.withinBudget = withinBudget;
        }
    }
}
