package com.innercosmos.ratelimit;

public interface RateLimitStore {
    RateLimitDecision consume(String key, RateLimitPolicy policy);
}
