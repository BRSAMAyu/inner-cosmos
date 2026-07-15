package com.innercosmos.ratelimit;

public record RateLimitDecision(boolean allowed, long remainingTokens) {
}
