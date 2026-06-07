package com.innercosmos.ai.proactive.dto;

/**
 * Represents a proactive candidate event trigger.
 */
public record ProactiveCandidate(
    String type,
    String triggerMeta,
    String suggestedContent
) {
    public ProactiveCandidate(String type, String triggerMeta) {
        this(type, triggerMeta, null);
    }
}