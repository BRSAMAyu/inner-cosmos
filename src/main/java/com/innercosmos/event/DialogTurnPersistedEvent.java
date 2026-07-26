package com.innercosmos.event;

/** Emitted once a complete user + Aurora turn has been durably written. */
public record DialogTurnPersistedEvent(Long userId, Long sessionId, Long revision) {
}
