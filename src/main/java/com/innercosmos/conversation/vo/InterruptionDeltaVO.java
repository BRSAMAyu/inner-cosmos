package com.innercosmos.conversation.vo;

import java.util.List;

/**
 * User-safe facts shared between an interrupted turn and its replacement.
 *
 * <p>Only text that actually reached the client may appear in delivered fields. Cancelled
 * bubbles contribute their purpose, never their unsent content.
 */
public class InterruptionDeltaVO {
    public List<String> deliveredContent = List.of();
    public String deliveredSummary = "";
    public List<String> cancelledBubblePurposes = List.of();
    public String newUserMessage = "";
    /** CONTINUE / PARK / SWITCH / MERGE. */
    public String continuityDecision = "PARK";
    public List<String> changedFacts = List.of();
    public List<String> mustNotRepeat = List.of();
    public int planRevision;
}
