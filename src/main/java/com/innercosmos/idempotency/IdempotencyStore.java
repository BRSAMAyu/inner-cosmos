package com.innercosmos.idempotency;

import java.time.Duration;

public interface IdempotencyStore {
    IdempotencyClaim claim(String key, String fingerprint, Duration ttl);
    void complete(String key, String fingerprint, CachedHttpResponse response, Duration ttl);
    void abort(String key, String fingerprint);
}
