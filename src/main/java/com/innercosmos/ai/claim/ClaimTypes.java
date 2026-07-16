package com.innercosmos.ai.claim;

import java.util.Set;

/**
 * Campaign B user-model claim types (doc-16 高维用户画像:
 * 事实、偏好、价值、关系、情绪模式、习惯、表达风格、需求、边界、变化趋势和不确定性).
 * Kept as string constants to match the free-string {@code tb_understanding_claim.claim_type} column.
 */
public final class ClaimTypes {
    public static final String FACT = "FACT";                 // 事实: stable self-facts
    public static final String PREFERENCE = "PREFERENCE";     // 偏好: likes/dislikes
    public static final String VALUE = "VALUE";               // 价值: what the user holds important
    public static final String RELATION = "RELATION";         // 关系: people and how they relate
    public static final String EMOTION_PATTERN = "EMOTION_PATTERN"; // 情绪模式: recurring emotional tendencies
    public static final String HABIT = "HABIT";               // 习惯: behavioral regularities
    public static final String EXPRESSION_STYLE = "EXPRESSION_STYLE"; // 表达风格
    public static final String NEED = "NEED";                 // 需求: wants/needs
    public static final String BOUNDARY = "BOUNDARY";         // 边界: limits, what is not acceptable
    public static final String TREND = "TREND";               // 变化趋势: change over time
    public static final String UNCERTAINTY = "UNCERTAINTY";   // 不确定性: explicitly unresolved self-questions

    public static final Set<String> ALL = Set.of(FACT, PREFERENCE, VALUE, RELATION, EMOTION_PATTERN,
            HABIT, EXPRESSION_STYLE, NEED, BOUNDARY, TREND, UNCERTAINTY);

    private ClaimTypes() {
    }
}
