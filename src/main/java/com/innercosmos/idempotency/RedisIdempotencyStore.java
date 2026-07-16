package com.innercosmos.idempotency;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "inner-cosmos.idempotency.redis.enabled", havingValue = "true")
public final class RedisIdempotencyStore implements IdempotencyStore {
    private static final DefaultRedisScript<List> CLAIM = new DefaultRedisScript<>("""
            local fp = redis.call('HGET', KEYS[1], 'fingerprint')
            if not fp then
              redis.call('HSET', KEYS[1], 'fingerprint', ARGV[1], 'state', 'IN_PROGRESS')
              redis.call('PEXPIRE', KEYS[1], ARGV[2])
              return {'ACQUIRED'}
            end
            if fp ~= ARGV[1] then return {'CONFLICT'} end
            local state = redis.call('HGET', KEYS[1], 'state')
            if state ~= 'COMPLETE' then return {'IN_PROGRESS'} end
            return {'REPLAY', redis.call('HGET', KEYS[1], 'status'),
                    redis.call('HGET', KEYS[1], 'content_type'), redis.call('HGET', KEYS[1], 'body'),
                    redis.call('HGET', KEYS[1], 'etag')}
            """, List.class);
    private static final DefaultRedisScript<Long> COMPLETE = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[1], 'fingerprint') ~= ARGV[1] then return 0 end
            redis.call('HSET', KEYS[1], 'state', 'COMPLETE', 'status', ARGV[2],
                       'content_type', ARGV[3], 'body', ARGV[4], 'etag', ARGV[5])
            redis.call('PEXPIRE', KEYS[1], ARGV[6])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> ABORT = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[1], 'fingerprint') == ARGV[1]
               and redis.call('HGET', KEYS[1], 'state') == 'IN_PROGRESS' then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final String namespace;

    public RedisIdempotencyStore(StringRedisTemplate redis,
                                 @Value("${inner-cosmos.idempotency.redis.namespace:inner-cosmos:idempotency:v1}")
                                 String namespace) {
        this.redis = redis;
        this.namespace = namespace;
    }

    @Override
    public IdempotencyClaim claim(String key, String fingerprint, Duration ttl) {
        try {
            List<?> result = redis.execute(CLAIM, List.of(redisKey(key)), fingerprint, Long.toString(ttl.toMillis()));
            if (result == null || result.isEmpty()) throw new IllegalStateException("empty Redis claim result");
            String state = String.valueOf(result.get(0));
            return switch (state) {
                case "ACQUIRED" -> IdempotencyClaim.acquired();
                case "CONFLICT" -> IdempotencyClaim.conflict();
                case "IN_PROGRESS" -> IdempotencyClaim.inProgress();
                case "REPLAY" -> IdempotencyClaim.replay(new CachedHttpResponse(
                        Integer.parseInt(String.valueOf(result.get(1))), nullable(result.get(2)),
                        Base64.getDecoder().decode(String.valueOf(result.get(3))), nullable(result.get(4))));
                default -> throw new IllegalStateException("unknown Redis claim state: " + state);
            };
        } catch (RuntimeException failure) {
            throw new IdempotencyStoreUnavailableException(failure);
        }
    }

    @Override
    public void complete(String key, String fingerprint, CachedHttpResponse response, Duration ttl) {
        try {
            redis.execute(COMPLETE, List.of(redisKey(key)), fingerprint, Integer.toString(response.status()),
                    response.contentType() == null ? "" : response.contentType(),
                    Base64.getEncoder().encodeToString(response.body()),
                    response.etag() == null ? "" : response.etag(), Long.toString(ttl.toMillis()));
        } catch (RuntimeException failure) {
            throw new IdempotencyStoreUnavailableException(failure);
        }
    }

    @Override
    public void abort(String key, String fingerprint) {
        try {
            redis.execute(ABORT, List.of(redisKey(key)), fingerprint);
        } catch (RuntimeException failure) {
            throw new IdempotencyStoreUnavailableException(failure);
        }
    }

    private String redisKey(String key) { return namespace + ":" + key; }
    private String nullable(Object value) {
        String text = value == null ? null : String.valueOf(value);
        return text == null || text.isBlank() ? null : text;
    }
}
