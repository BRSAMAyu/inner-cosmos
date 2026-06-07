package com.innercosmos.ai.goodbye;

/**
 * Result of the goodbye flow.
 * @param success whether the goodbye was triggered successfully
 * @param line the goodbye line spoken by Aurora
 * @param stepsCompleted list of completed async steps
 * @param confirmed whether user confirmed medium-confidence goodbye
 * @param reverted whether goodbye was reverted
 * @param confidence confidence level of the goodbye trigger
 */
public record GoodbyeResult(
        boolean success,
        String line,
        java.util.List<String> stepsCompleted,
        boolean confirmed,
        boolean reverted,
        double confidence
) {}