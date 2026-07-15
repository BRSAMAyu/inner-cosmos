package com.innercosmos.ratelimit;

public final class RateLimitStoreUnavailableException extends RuntimeException {
    public RateLimitStoreUnavailableException(Throwable cause) {
        super("Distributed rate-limit store is unavailable", cause);
    }
}
