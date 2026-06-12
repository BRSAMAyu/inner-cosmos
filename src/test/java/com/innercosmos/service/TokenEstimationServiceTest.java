package com.innercosmos.service;

import com.innercosmos.service.impl.TokenEstimationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenEstimationServiceTest {

    private TokenEstimationServiceImpl tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new TokenEstimationServiceImpl();
    }

    // --- estimateTokens ---

    @Test
    @DisplayName("estimateTokens returns 0 for null text")
    void estimateTokens_nullText_returnsZero() {
        assertEquals(0, tokenService.estimateTokens(null));
    }

    @Test
    @DisplayName("estimateTokens returns 0 for empty string")
    void estimateTokens_emptyString_returnsZero() {
        assertEquals(0, tokenService.estimateTokens(""));
    }

    @Test
    @DisplayName("estimateTokens returns 0 for blank string")
    void estimateTokens_blankString_returnsZero() {
        assertEquals(0, tokenService.estimateTokens("   "));
    }

    @Test
    @DisplayName("estimateTokens counts English words correctly")
    void estimateTokens_englishText_countsWords() {
        // "Hello world" = 2 English words
        int tokens = tokenService.estimateTokens("Hello world");
        int expected = (int) Math.ceil(2 * 1.3); // 2.6 -> 3
        assertEquals(expected, tokens);
    }

    @Test
    @DisplayName("estimateTokens counts Chinese characters correctly")
    void estimateTokens_chineseText_countsChars() {
        // Build string from char codes to avoid encoding issues
        // U+4F60 = ni, U+597D = hao (2 Chinese chars)
        String text = String.valueOf(new char[]{0x4F60, 0x597D});
        int tokens = tokenService.estimateTokens(text);
        int expected = (int) Math.ceil(2 * 2.5); // 5.0 -> 5
        assertEquals(expected, tokens);
    }

    @Test
    @DisplayName("estimateTokens handles mixed Chinese and English")
    void estimateTokens_mixedContent_countsBoth() {
        // 2 Chinese chars + 1 English word "hello"
        String chinese = String.valueOf(new char[]{0x4F60, 0x597D});
        String text = chinese + " hello";
        int tokens = tokenService.estimateTokens(text);
        int expected = (int) Math.ceil(2 * 2.5 + 1 * 1.3); // 5.0 + 1.3 = 6.3 -> 7
        assertEquals(expected, tokens);
    }

    @Test
    @DisplayName("estimateTokens handles long text")
    void estimateTokens_longText_returnsPositiveValue() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("word ");
        }
        int tokens = tokenService.estimateTokens(sb.toString());
        assertTrue(tokens > 0, "Long text should produce positive token count");
    }

    @Test
    @DisplayName("estimateTokens counts digits as part of words")
    void estimateTokens_digitsCountedAsWords() {
        // "abc123 xyz456" = 2 word groups
        int tokens = tokenService.estimateTokens("abc123 xyz456");
        int expected = (int) Math.ceil(2 * 1.3); // 2.6 -> 3
        assertEquals(expected, tokens);
    }

    // --- calculatePromptTokens ---

    @Test
    @DisplayName("calculatePromptTokens adds overhead for formatting")
    void calculatePromptTokens_addsOverhead() {
        int tokens = tokenService.calculatePromptTokens("Hello", "World");
        int systemTokens = tokenService.estimateTokens("Hello");
        int userTokens = tokenService.estimateTokens("World");
        assertEquals(systemTokens + userTokens + 10, tokens);
    }

    @Test
    @DisplayName("calculatePromptTokens handles null inputs")
    void calculatePromptTokens_nullInputs_handled() {
        int tokens = tokenService.calculatePromptTokens(null, null);
        // Both null => 0 + 0 + 10 = 10
        assertEquals(10, tokens);
    }

    @Test
    @DisplayName("calculatePromptTokens handles one null input")
    void calculatePromptTokens_oneNullInput_handled() {
        int tokens = tokenService.calculatePromptTokens("Hello", null);
        int expected = tokenService.estimateTokens("Hello") + 0 + 10;
        assertEquals(expected, tokens);
    }

    // --- estimateResponseTokens ---

    @Test
    @DisplayName("estimateResponseTokens for PERSONA_CHAT mode")
    void estimateResponseTokens_personaChat_returnsExpected() {
        int promptTokens = tokenService.estimateTokens("Hello world");
        int expected = (int) Math.ceil(promptTokens * 0.8);
        assertEquals(expected, tokenService.estimateResponseTokens("Hello world", "PERSONA_CHAT"));
    }

    @Test
    @DisplayName("estimateResponseTokens for unknown mode uses default multiplier")
    void estimateResponseTokens_unknownMode_usesDefault() {
        int promptTokens = tokenService.estimateTokens("Hello world");
        int expected = (int) Math.ceil(promptTokens * 0.5);
        assertEquals(expected, tokenService.estimateResponseTokens("Hello world", "UNKNOWN_MODE"));
    }

    // --- getDailyUsage ---

    @Test
    @DisplayName("getDailyUsage returns zero stats for new user")
    void getDailyUsage_newUser_returnsZeroStats() {
        TokenEstimationService.TokenUsageStats stats = tokenService.getDailyUsage(999L);
        assertEquals(0, stats.totalTokens);
        assertEquals(0, stats.promptTokens);
        assertEquals(0, stats.responseTokens);
        assertEquals(0.0, stats.estimatedCost, 0.001);
        assertEquals(0, stats.requestCount);
    }

    @Test
    @DisplayName("getDailyUsage returns accumulated stats after recording")
    void getDailyUsage_afterRecordUsage_returnsAccumulatedStats() {
        tokenService.recordUsage(1L, "mock", 100, 50);
        tokenService.recordUsage(1L, "mock", 200, 100);
        TokenEstimationService.TokenUsageStats stats = tokenService.getDailyUsage(1L);
        assertEquals(450, stats.totalTokens);
        assertEquals(300, stats.promptTokens);
        assertEquals(150, stats.responseTokens);
        assertEquals(2, stats.requestCount);
    }

    // --- isWithinBudget ---

    @Test
    @DisplayName("isWithinBudget returns true for new user")
    void isWithinBudget_newUser_returnsTrue() {
        assertTrue(tokenService.isWithinBudget(999L));
    }

    @Test
    @DisplayName("isWithinBudget returns false when usage exceeds 100k tokens")
    void isWithinBudget_exceedsBudget_returnsFalse() {
        tokenService.recordUsage(1L, "mock", 50000, 50000);
        assertFalse(tokenService.isWithinBudget(1L));
    }

    @Test
    @DisplayName("isWithinBudget returns true when usage is under budget")
    void isWithinBudget_underBudget_returnsTrue() {
        tokenService.recordUsage(2L, "mock", 100, 50);
        assertTrue(tokenService.isWithinBudget(2L));
    }

    // --- recordUsage ---

    @Test
    @DisplayName("recordUsage accumulates across multiple calls")
    void recordUsage_accumulatesAcrossCalls() {
        tokenService.recordUsage(1L, "mock", 100, 50);
        tokenService.recordUsage(1L, "mock", 200, 100);
        TokenEstimationService.TokenUsageStats stats = tokenService.getDailyUsage(1L);
        assertEquals(450, stats.totalTokens);
        assertEquals(2, stats.requestCount);
    }

    @Test
    @DisplayName("recordUsage for different users is tracked separately")
    void recordUsage_differentUsers_trackedSeparately() {
        tokenService.recordUsage(1L, "mock", 100, 50);
        tokenService.recordUsage(2L, "mock", 200, 100);
        TokenEstimationService.TokenUsageStats stats1 = tokenService.getDailyUsage(1L);
        TokenEstimationService.TokenUsageStats stats2 = tokenService.getDailyUsage(2L);
        assertEquals(150, stats1.totalTokens);
        assertEquals(300, stats2.totalTokens);
    }

    // --- estimateCost ---

    @Test
    @DisplayName("estimateCost returns zero for mock model")
    void estimateCost_mockModel_returnsZero() {
        double cost = tokenService.estimateCost(1000, "mock");
        assertEquals(0.0, cost, 0.001);
    }

    @Test
    @DisplayName("estimateCost returns positive cost for known model")
    void estimateCost_knownModel_returnsPositiveCost() {
        double cost = tokenService.estimateCost(1000, "deepseek-chat");
        assertTrue(cost > 0, "Known model should have positive cost");
    }

    // --- getForecast ---

    @Test
    @DisplayName("getForecast returns reasonable values for new user")
    void getForecast_newUser_returnsDefaults() {
        TokenEstimationService.UsageForecast forecast = tokenService.getForecast(999L);
        assertTrue(forecast.projectedDailyTokens > 0);
        assertTrue(forecast.remainingDaysInMonth > 0);
    }

    @Test
    @DisplayName("getForecast returns within budget for low usage user")
    void getForecast_lowUsage_withinBudget() {
        tokenService.recordUsage(1L, "mock", 10, 5);
        TokenEstimationService.UsageForecast forecast = tokenService.getForecast(1L);
        assertTrue(forecast.withinBudget);
    }
}
