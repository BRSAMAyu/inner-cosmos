package com.innercosmos.ai.proactive.dto;

/**
 * Represents the LLM-driven ALIVE decision.
 */
public record AliveDecision(
    String decide, // "push" | "wait" | "schedule"
    int waitMinutes,
    String contentForUser,
    String reasonInternal
) {
    public static AliveDecision wait(int minutes, String reason) {
        return new AliveDecision("wait", minutes, null, reason);
    }

    public static AliveDecision push(String content, String reason) {
        return new AliveDecision("push", 0, content, reason);
    }

    public static AliveDecision schedule(int minutes, String reason) {
        return new AliveDecision("schedule", minutes, null, reason);
    }
}