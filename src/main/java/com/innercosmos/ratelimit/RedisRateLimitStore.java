package com.innercosmos.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/** Atomic, Redis-server-time token bucket shared by every API pod. */
@Component
@ConditionalOnProperty(name = "inner-cosmos.security.rate-limit.redis.enabled", havingValue = "true")
public final class RedisRateLimitStore implements RateLimitStore {
    private static final DefaultRedisScript<List> TOKEN_BUCKET = new DefaultRedisScript<>("""
            local now = redis.call('TIME')
            local now_ms = (tonumber(now[1]) * 1000) + math.floor(tonumber(now[2]) / 1000)
            local capacity = tonumber(ARGV[1])
            local refill_per_ms = tonumber(ARGV[2]) / 60000
            local state = redis.call('HMGET', KEYS[1], 'tokens', 'updated_at')
            local tokens = tonumber(state[1])
            local updated_at = tonumber(state[2])
            if tokens == nil then tokens = capacity end
            if updated_at == nil then updated_at = now_ms end
            local elapsed = math.max(0, now_ms - updated_at)
            tokens = math.min(capacity, tokens + (elapsed * refill_per_ms))
            local allowed = 0
            if tokens >= 1 then
              tokens = tokens - 1
              allowed = 1
            end
            redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'updated_at', tostring(now_ms))
            redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[3]))
            return {allowed, math.floor(tokens)}
            """, List.class);

    private final StringRedisTemplate redis;
    private final String namespace;

    public RedisRateLimitStore(StringRedisTemplate redis,
                               @Value("${inner-cosmos.security.rate-limit.redis.namespace:inner-cosmos:rate-limit:v1}")
                               String namespace) {
        this.redis = redis;
        this.namespace = namespace;
    }

    @Override
    public RateLimitDecision consume(String key, RateLimitPolicy policy) {
        try {
            List<?> result = redis.execute(TOKEN_BUCKET, List.of(namespace + ":" + key),
                    Long.toString(policy.capacity()),
                    Long.toString(policy.refillPerMinute()),
                    "120000");
            if (result == null || result.size() != 2) {
                throw new IllegalStateException("Redis rate-limit script returned an invalid result");
            }
            return new RateLimitDecision(asLong(result.get(0)) == 1, asLong(result.get(1)));
        } catch (RuntimeException failure) {
            throw new RateLimitStoreUnavailableException(failure);
        }
    }

    private long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
    }
}
