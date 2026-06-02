package com.innercosmos.service.impl;

import com.innercosmos.service.TokenEstimationService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of TokenEstimationService with estimation and budget control.
 */
@Service
public class TokenEstimationServiceImpl implements TokenEstimationService {

    private static final double CHINESE_CHAR_TOKEN_RATIO = 2.5;
    private static final double ENGLISH_WORD_TOKEN_RATIO = 1.3;
    private static final double TOKEN_PER_DOLLAR = 50000; // Approximate rate

    private final Map<Long, DailyUsage> dailyUsageStore = new ConcurrentHashMap<>();
    private final Map<String, ModelCost> modelCosts = new HashMap<>();

    public TokenEstimationServiceImpl() {
        // Initialize model costs
        modelCosts.put("MiniMax-M2.5", new ModelCost(0.0001, 0.0002)); // prompt/response per 1k tokens
        modelCosts.put("glm-4-flash", new ModelCost(0.0001, 0.0002));
        modelCosts.put("deepseek-chat", new ModelCost(0.00014, 0.00028));
        modelCosts.put("mock", new ModelCost(0, 0));
    }

    @Override
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;

        int chineseChars = 0;
        int englishWords = 0;

        // Count Chinese characters and English words
        boolean inEnglishWord = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isChinese(c)) {
                chineseChars++;
                inEnglishWord = false;
            } else if (Character.isLetterOrDigit(c)) {
                if (!inEnglishWord) {
                    englishWords++;
                    inEnglishWord = true;
                }
            } else {
                inEnglishWord = false;
            }
        }

        return (int) Math.ceil(chineseChars * CHINESE_CHAR_TOKEN_RATIO + englishWords * ENGLISH_WORD_TOKEN_RATIO);
    }

    @Override
    public int calculatePromptTokens(String systemPrompt, String userPrompt) {
        int systemTokens = estimateTokens(systemPrompt);
        int userTokens = estimateTokens(userPrompt);
        return systemTokens + userTokens + 10; // Add overhead for formatting
    }

    @Override
    public int estimateResponseTokens(String prompt, String mode) {
        int promptTokens = estimateTokens(prompt);

        // Response size varies by mode
        double multiplier = switch (mode) {
            case "PERSONA_CHAT" -> 0.8;
            case "BELIEF_EXTRACT" -> 0.4;
            case "EMOTION_AGGREGATE" -> 0.3;
            case "THEME_CLUSTER" -> 0.6;
            case "MEMORY_EXTRACT" -> 0.5;
            case "SAFETY_REVIEW" -> 0.2;
            default -> 0.5;
        };

        return (int) Math.ceil(promptTokens * multiplier);
    }

    @Override
    public TokenUsageStats getDailyUsage(Long userId) {
        DailyUsage usage = dailyUsageStore.getOrDefault(userId, new DailyUsage());
        return new TokenUsageStats(
                usage.totalTokens,
                usage.promptTokens,
                usage.responseTokens,
                usage.estimatedCost,
                usage.requestCount
        );
    }

    @Override
    public boolean isWithinBudget(Long userId) {
        DailyUsage usage = dailyUsageStore.getOrDefault(userId, new DailyUsage());
        // Default daily budget: 100k tokens (~$2)
        return usage.totalTokens < 100000;
    }

    @Override
    public void recordUsage(Long userId, String mode, int promptTokens, int responseTokens) {
        DailyUsage usage = dailyUsageStore.computeIfAbsent(userId, k -> new DailyUsage());
        usage.promptTokens += promptTokens;
        usage.responseTokens += responseTokens;
        usage.totalTokens += (promptTokens + responseTokens);
        usage.requestCount++;

        double cost = estimateCost(promptTokens + responseTokens, mode);
        usage.estimatedCost += cost;
    }

    @Override
    public double estimateCost(int totalTokens, String model) {
        ModelCost cost = modelCosts.getOrDefault(model, modelCosts.get("mock"));
        double costPer1k = (cost.promptCost + cost.responseCost) / 2;
        return (totalTokens / 1000.0) * costPer1k;
    }

    @Override
    public UsageForecast getForecast(Long userId) {
        DailyUsage usage = dailyUsageStore.getOrDefault(userId, new DailyUsage());
        int currentDayOfMonth = LocalDate.now().getDayOfMonth();
        int remainingDays = 30 - currentDayOfMonth;

        int projectedDaily = usage.requestCount > 0 ? usage.totalTokens / usage.requestCount : 500;
        int projectedMonthly = usage.totalTokens + (projectedDaily * remainingDays);

        return new UsageForecast(
                projectedDaily,
                remainingDays,
                projectedMonthly,
                projectedMonthly < 3000000 // ~3M tokens monthly budget
        );
    }

    private boolean isChinese(char c) {
        return c >= 0x4E00 && c <= 0x9FFF;
    }

    private static class DailyUsage {
        int totalTokens;
        int promptTokens;
        int responseTokens;
        double estimatedCost;
        int requestCount;
    }

    private static class ModelCost {
        double promptCost;
        double responseCost;

        ModelCost(double promptCost, double responseCost) {
            this.promptCost = promptCost;
            this.responseCost = responseCost;
        }
    }
}
