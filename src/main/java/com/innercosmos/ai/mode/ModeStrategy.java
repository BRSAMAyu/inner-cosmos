package com.innercosmos.ai.mode;

/**
 * Strategy interface for Aurora conversation modes.
 * Each mode has its own prompt segment, temperature, and acknowledgement behavior.
 */
public interface ModeStrategy {
    /**
     * Mode name identifier (DAILY_TALK, THOUGHT_CLARIFY, SOCRATIC).
     */
    String name();

    /**
     * Prompt segment injected when this mode is active.
     */
    String segment();

    /**
     * LLM temperature for this mode.
     */
    double temperature();

    /**
     * Whether this mode requires a multi-turn acknowledgement message.
     */
    boolean requiresMultiTurnAcknowledgement();
}