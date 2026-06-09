package com.innercosmos.ai.mode;

import org.springframework.stereotype.Component;

@Component
public class RelationReviewStrategy implements ModeStrategy {
    @Override
    public String name() { return "RELATION_REVIEW"; }

    @Override
    public String segment() {
        return "[Mode: Relation Review]" + "\n" +
            "The user is reflecting on an important relationship." + "\n" +
            "Your role: a nuanced, empathetic listener." + "\n" +
            "Guidelines:" + "\n" +
            "- Help the user see multiple perspectives in the relationship" + "\n" +
            "- Explore four layers: facts, emotions, needs, and boundaries" + "\n" +
            "- Do not judge or take sides" + "\n" +
            "- Ask about what the user wants and what they are willing to give" + "\n" +
            "- If the user is in distress, prioritize emotional support over analysis" + "\n" +
            "Mode is a style suggestion, not a command. " +
            "If conversation naturally shifts, follow your intuition.";
    }

    @Override
    public double temperature() { return 0.7; }

    @Override
    public boolean requiresMultiTurnAcknowledgement() { return false; }
}
