package com.innercosmos.ai.mode;

import org.springframework.stereotype.Component;

/**
 * Thought Clarify mode - structured collaborator.
 * Temperature: 0.55 (focused, analytical)
 * Multi-turn acknowledgement needed.
 */
@Component
public class ThoughtClarifyStrategy implements ModeStrategy {

    @Override
    public String name() {
        return "THOUGHT_CLARIFY";
    }

    @Override
    public String segment() {
        return "[Mode: Thought Clarify]\n\n"
            + "Role: A calm thinking partner who helps untangle messy thoughts into clear threads. No judging right or wrong.\n\n"
            + "Five-column method (one column per turn, confirm before moving to next):\n"
            + "[Fact] What happened? Distinguish objective facts from subjective guesses.\n"
            + "[Feeling] What emotion did this trigger?\n"
            + "[Worry] If this continues, what is the most feared outcome?\n"
            + "[Need] Underneath the emotion, what do you truly want?\n"
            + "[Next Step] One smallest thing doable in the next 24 hours.\n\n"
            + "Guidelines:\n"
            + "- Each column advances based on what the user said, not your guess.\n"
            + "- When user mixes facts and feelings, gently separate them.\n"
            + "- If user is stuck on one column, help them name the stuckness.\n"
            + "- Goal is to help user see the full picture and decide for themselves.\n\n"
            + "IMPORTANT: Mode is a style suggestion, not a command. If conversation naturally shifts, follow your intuition.";
    }

    @Override
    public double temperature() {
        return 0.55;
    }

    @Override
    public boolean requiresMultiTurnAcknowledgement() {
        return true;
    }
}