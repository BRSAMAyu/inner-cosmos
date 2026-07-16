package com.innercosmos.idempotency;

public record IdempotencyClaim(State state, CachedHttpResponse response) {
    public enum State { ACQUIRED, REPLAY, IN_PROGRESS, CONFLICT }

    public static IdempotencyClaim acquired() { return new IdempotencyClaim(State.ACQUIRED, null); }
    public static IdempotencyClaim replay(CachedHttpResponse response) { return new IdempotencyClaim(State.REPLAY, response); }
    public static IdempotencyClaim inProgress() { return new IdempotencyClaim(State.IN_PROGRESS, null); }
    public static IdempotencyClaim conflict() { return new IdempotencyClaim(State.CONFLICT, null); }
}
