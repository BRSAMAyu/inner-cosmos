package com.innercosmos.idempotency;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "inner-cosmos.idempotency.redis.enabled", havingValue = "false", matchIfMissing = true)
public final class InMemoryIdempotencyStore implements IdempotencyStore {
    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public IdempotencyClaim claim(String key, String fingerprint, Duration ttl) {
        long now = System.currentTimeMillis();
        AtomicReference<IdempotencyClaim> result = new AtomicReference<>();
        entries.compute(key, (ignored, existing) -> {
            if (existing == null || existing.expiresAt <= now) {
                result.set(IdempotencyClaim.acquired());
                return new Entry(fingerprint, null, now + ttl.toMillis());
            }
            if (!existing.fingerprint.equals(fingerprint)) {
                result.set(IdempotencyClaim.conflict());
            } else if (existing.response != null) {
                result.set(IdempotencyClaim.replay(existing.response));
            } else {
                result.set(IdempotencyClaim.inProgress());
            }
            return existing;
        });
        return result.get();
    }

    @Override
    public void complete(String key, String fingerprint, CachedHttpResponse response, Duration ttl) {
        long expiresAt = System.currentTimeMillis() + ttl.toMillis();
        entries.computeIfPresent(key, (ignored, entry) -> entry.fingerprint.equals(fingerprint)
                ? new Entry(fingerprint, response, expiresAt) : entry);
    }

    @Override
    public void abort(String key, String fingerprint) {
        entries.computeIfPresent(key, (ignored, entry) -> entry.fingerprint.equals(fingerprint)
                && entry.response == null ? null : entry);
    }

    private record Entry(String fingerprint, CachedHttpResponse response, long expiresAt) {}
}
