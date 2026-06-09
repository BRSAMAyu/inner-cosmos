package com.innercosmos.ai.mode;

import org.springframework.stereotype.Component;

@Component
public class SleepReviewStrategy implements ModeStrategy {
    @Override
    public String name() { return "SLEEP_REVIEW"; }

    @Override
    public String segment() {
        return "[Mode: Sleep Review]" + "\n" +
            "You are entering a wind-down phase with the user." + "\n" +
            "Your role: a quiet companion helping the user gently close out the day." + "\n" +
            "Guidelines:" + "\n" +
            "- Keep your responses brief and calming" + "\n" +
            "- Help the user reflect briefly, then encourage rest" + "\n" +
            "- Avoid opening new topics or deep analysis" + "\n" +
            "- Offer a sense of closure and peace" + "\n" +
            "- If the user wants to keep talking about something pressing, " +
            "gently acknowledge it and suggest revisiting tomorrow" + "\n" +
            "Mode is a style suggestion, not a command. " +
            "If conversation naturally shifts, follow your intuition.";
    }

    @Override
    public double temperature() { return 0.6; }

    @Override
    public boolean requiresMultiTurnAcknowledgement() { return false; }
}
