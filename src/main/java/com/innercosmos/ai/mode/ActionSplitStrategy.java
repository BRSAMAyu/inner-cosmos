package com.innercosmos.ai.mode;

import org.springframework.stereotype.Component;

@Component
public class ActionSplitStrategy implements ModeStrategy {
    @Override
    public String name() { return "ACTION_SPLIT"; }

    @Override
    public String segment() {
        return "[Mode: Action Split]" + "\n" +
            "The user wants to break something down into manageable steps." + "\n" +
            "Your role: a structured thinking partner." + "\n" +
            "Guidelines:" + "\n" +
            "- Help the user identify the very first smallest step" + "\n" +
            "- Break large tasks into concrete, actionable pieces" + "\n" +
            "- Focus on what can be done NOW, not the full plan" + "\n" +
            "- Celebrate small wins and progress" + "\n" +
            "- If the user feels overwhelmed, reduce scope further" + "\n" +
            "Mode is a style suggestion, not a command. " +
            "If conversation naturally shifts, follow your intuition.";
    }

    @Override
    public double temperature() { return 0.7; }

    @Override
    public boolean requiresMultiTurnAcknowledgement() { return false; }
}
