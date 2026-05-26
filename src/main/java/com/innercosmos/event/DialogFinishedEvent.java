package com.innercosmos.event;

public class DialogFinishedEvent {
    public final Long userId;
    public final Long sessionId;

    public DialogFinishedEvent(Long userId, Long sessionId) {
        this.userId = userId;
        this.sessionId = sessionId;
    }
}
