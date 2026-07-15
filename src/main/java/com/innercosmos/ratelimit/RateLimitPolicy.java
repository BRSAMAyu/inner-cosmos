package com.innercosmos.ratelimit;

public record RateLimitPolicy(long capacity, long refillPerMinute, long advertisedLimit) {
    public RateLimitPolicy {
        if (capacity <= 0 || refillPerMinute <= 0 || advertisedLimit <= 0) {
            throw new IllegalArgumentException("Rate-limit values must be positive");
        }
    }
}
