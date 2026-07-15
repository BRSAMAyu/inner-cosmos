package com.innercosmos.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Offline-safe development implementation. Production is required to use Redis. */
@Component
@ConditionalOnProperty(name = "inner-cosmos.security.rate-limit.redis.enabled",
        havingValue = "false", matchIfMissing = true)
public final class InMemoryRateLimitStore implements RateLimitStore {
    private final Map<String, Entry> buckets = new ConcurrentHashMap<>();

    @Override
    public RateLimitDecision consume(String key, RateLimitPolicy policy) {
        Entry entry = buckets.compute(key, (ignored, current) -> {
            if (current == null || !current.policy.equals(policy)) {
                return new Entry(create(policy), policy);
            }
            current.lastAccess = System.currentTimeMillis();
            return current;
        });
        boolean allowed = entry.bucket.tryConsume(1);
        return new RateLimitDecision(allowed, entry.bucket.getAvailableTokens());
    }

    @Scheduled(fixedDelay = 300_000)
    void evictStaleBuckets() {
        long threshold = System.currentTimeMillis() - 3_600_000;
        Iterator<Map.Entry<String, Entry>> iterator = buckets.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().lastAccess < threshold) {
                iterator.remove();
            }
        }
    }

    @PreDestroy
    void clear() {
        buckets.clear();
    }

    private Bucket create(RateLimitPolicy policy) {
        return Bucket.builder().addLimit(Bandwidth.builder()
                .capacity(policy.capacity())
                .refillGreedy(policy.refillPerMinute(), Duration.ofMinutes(1))
                .build()).build();
    }

    private static final class Entry {
        private final Bucket bucket;
        private final RateLimitPolicy policy;
        private volatile long lastAccess = System.currentTimeMillis();

        private Entry(Bucket bucket, RateLimitPolicy policy) {
            this.bucket = bucket;
            this.policy = policy;
        }
    }
}
