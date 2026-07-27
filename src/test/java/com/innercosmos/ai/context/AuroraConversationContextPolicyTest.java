package com.innercosmos.ai.context;

import com.innercosmos.config.LlmConfig;
import com.innercosmos.service.impl.TokenEstimationServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuroraConversationContextPolicyTest {

    @Test
    void preservesTheWholeChronologicalSessionBelowTheProviderSafeLimit() {
        LlmConfig config = config(200_000, 1_000_000);
        AuroraConversationContextPolicy policy =
                new AuroraConversationContextPolicy(config, new TokenEstimationServiceImpl());
        List<String> history = new ArrayList<>();
        for (int i = 1; i <= 120; i++) {
            history.add("#" + i + " " + (i % 2 == 0 ? "Aurora" : "用户") + "：完整消息-" + i);
        }

        var selected = policy.select(history, "当前新消息", "stable-system",
                Map.of("userMessage", "当前新消息"), "deepseek", "deepseek-v4-flash");

        assertThat(selected.truncated()).isFalse();
        assertThat(selected.omittedMessageCount()).isZero();
        assertThat(selected.messages()).containsExactlyElementsOf(history);
        assertThat(selected.inputTokenLimit()).isEqualTo(200_000);
    }

    @Test
    void currentPersistedUserMessageIsNotDuplicatedInHistory() {
        AuroraConversationContextPolicy policy =
                new AuroraConversationContextPolicy(config(200_000, 200_000),
                        new TokenEstimationServiceImpl());

        var selected = policy.select(List.of(
                        "#1 用户：上一句",
                        "#2 Aurora：上一答",
                        "#3 用户：当前一句"),
                "当前一句", "system", Map.of("userMessage", "当前一句"),
                "glm", "glm-4.7");

        assertThat(selected.messages()).containsExactly("#1 用户：上一句", "#2 Aurora：上一答");
    }

    @Test
    void providerWindowSubtractsOutputAndSafetyReservesBeforeHistory() {
        LlmConfig config = config(200_000, 20_000);
        config.context.outputReserveTokens = 3_000;
        config.context.safetyMarginTokens = 2_000;
        AuroraConversationContextPolicy policy =
                new AuroraConversationContextPolicy(config, new TokenEstimationServiceImpl());
        List<String> history = java.util.stream.IntStream.range(0, 2_000)
                .mapToObj(i -> "#" + i + " 用户：" + "长会话内容".repeat(12))
                .toList();

        var selected = policy.select(history, "现在", "system",
                Map.of("userMessage", "现在"), "glm", "test-small-window");

        assertThat(selected.inputTokenLimit()).isEqualTo(15_000);
        assertThat(selected.fixedContextTokens() + selected.historyTokens())
                .isLessThanOrEqualTo(selected.inputTokenLimit());
        assertThat(selected.truncated()).isTrue();
    }

    @Test
    void overflowMakesTheBoundaryExplicitAndKeepsOpeningCriticalAnchorsAndTail() {
        LlmConfig config = config(2_400, 10_000);
        config.context.outputReserveTokens = 100;
        config.context.safetyMarginTokens = 100;
        config.context.openingAnchorTokens = 300;
        config.context.criticalAnchorTokens = 500;
        AuroraConversationContextPolicy policy =
                new AuroraConversationContextPolicy(config, new TokenEstimationServiceImpl());
        List<String> history = new ArrayList<>();
        history.add("#1 用户：这是会话开场，我想认真聊一件事");
        history.add("#2 Aurora：我在这里");
        for (int i = 3; i < 40; i++) history.add("#" + i + " 用户：" + "普通中段内容".repeat(8));
        history.add("#40 用户：请记住，我们约定周五前不替我做决定");
        history.add("#41 用户：我在读《驱魔人》，在意梅林神父的选择");
        for (int i = 42; i < 70; i++) history.add("#" + i + " Aurora：" + "后续对话".repeat(8));
        history.add("#70 用户：这是最近的问题");

        var selected = policy.select(history, "新的当前问题", "system",
                Map.of("userMessage", "新的当前问题"), "deepseek", "deepseek-v4-flash");

        assertThat(selected.truncated()).isTrue();
        assertThat(selected.messages().get(0)).isEqualTo(history.get(0));
        assertThat(selected.messages()).anyMatch(value ->
                value.startsWith(AuroraConversationContextPolicy.TRUNCATION_MARKER_PREFIX));
        assertThat(selected.messages()).contains(
                "#40 用户：请记住，我们约定周五前不替我做决定",
                "#41 用户：我在读《驱魔人》，在意梅林神父的选择",
                "#70 用户：这是最近的问题");
        assertThat(selected.omittedMessageCount()).isGreaterThan(0);
    }

    @Test
    void appendingOneTurnDoesNotReorderTheExistingCachePrefix() {
        AuroraConversationContextPolicy policy =
                new AuroraConversationContextPolicy(config(200_000, 1_000_000),
                        new TokenEstimationServiceImpl());
        List<String> first = List.of("#1 用户：开场", "#2 Aurora：回应", "#3 用户：继续");
        List<String> appended = new ArrayList<>(first);
        appended.add("#4 Aurora：新的回应");

        var before = policy.select(first, "下一问", "system",
                new LinkedHashMap<>(Map.of("userMessage", "下一问")), "mimo", "mimo-v2.5");
        var after = policy.select(appended, "再下一问", "system",
                new LinkedHashMap<>(Map.of("userMessage", "再下一问")), "mimo", "mimo-v2.5");

        assertThat(after.messages().subList(0, before.messages().size()))
                .containsExactlyElementsOf(before.messages());
    }

    @Test
    void oversizedCurrentMessageIsExplicitlyBoundedWithoutPretendingItWasComplete() {
        LlmConfig config = config(2_000, 10_000);
        AuroraConversationContextPolicy policy =
                new AuroraConversationContextPolicy(config, new TokenEstimationServiceImpl());
        String oversized = "《驱魔人》里的这一段".repeat(2_000);

        var selected = policy.select(List.of("#1 用户：会话开场"), oversized, "system",
                Map.of("userMessage", oversized, "mode", "DAILY_TALK"),
                "deepseek", "deepseek-v4-flash");

        assertThat(selected.currentMessageTruncated()).isTrue();
        assertThat(selected.modelUserMessage())
                .contains(AuroraConversationContextPolicy.CURRENT_MESSAGE_TRUNCATION_MARKER)
                .startsWith("《驱魔人》")
                .endsWith("这一段");
        assertThat(selected.fixedContextTokens() + selected.historyTokens())
                .isLessThanOrEqualTo(selected.inputTokenLimit());
    }

    private static LlmConfig config(int hardInputLimit, int providerWindow) {
        LlmConfig config = new LlmConfig();
        config.context.hardMaxInputTokens = hardInputLimit;
        config.context.outputReserveTokens = 0;
        config.context.safetyMarginTokens = 0;
        config.context.providerWindowTokens = new LinkedHashMap<>(Map.of(
                "deepseek", providerWindow,
                "glm", providerWindow,
                "mimo", providerWindow,
                "minimax", providerWindow,
                "mock", providerWindow));
        return config;
    }
}
