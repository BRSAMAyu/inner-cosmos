package com.innercosmos.vo;

import java.util.List;

/**
 * Value Object representing a chat response from a Persona (Echo Capsule).
 * Contains the reply message, trust level, and turn limit status.
 */
public class PersonaChatVO {
    private String reply;
    private Integer remainingTurns;
    private Double trustLevel;
    private String personaName;
    private List<String> suggestedTopics;
    private Boolean shouldEnd;

    public PersonaChatVO() {}

    public PersonaChatVO(String reply, Integer remainingTurns, Double trustLevel) {
        this.reply = reply;
        this.remainingTurns = remainingTurns;
        this.trustLevel = trustLevel;
    }

    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }

    public Integer getRemainingTurns() { return remainingTurns; }
    public void setRemainingTurns(Integer remainingTurns) { this.remainingTurns = remainingTurns; }

    public Double getTrustLevel() { return trustLevel; }
    public void setTrustLevel(Double trustLevel) { this.trustLevel = trustLevel; }

    public String getPersonaName() { return personaName; }
    public void setPersonaName(String personaName) { this.personaName = personaName; }

    public List<String> getSuggestedTopics() { return suggestedTopics; }
    public void setSuggestedTopics(List<String> suggestedTopics) { this.suggestedTopics = suggestedTopics; }

    public Boolean getShouldEnd() { return shouldEnd; }
    public void setShouldEnd(Boolean shouldEnd) { this.shouldEnd = shouldEnd; }
}
