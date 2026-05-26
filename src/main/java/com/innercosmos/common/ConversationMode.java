package com.innercosmos.common;

public enum ConversationMode {
    DAILY_TALK, THOUGHT_CLARIFY, SLEEP_REVIEW, SOCRATIC, ACTION_SPLIT, RELATION_REVIEW;

    public static ConversationMode fromString(String value) {
        if (value == null) return DAILY_TALK;
        try { return valueOf(value.toUpperCase()); }
        catch (IllegalArgumentException e) { return DAILY_TALK; }
    }
}
