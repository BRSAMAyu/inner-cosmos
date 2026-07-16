package com.innercosmos.streaming;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.dto.ChatRequest;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "inner-cosmos.aurora.stream.redis.enabled", havingValue = "true")
public class RedisAuroraStreamStageStore implements AuroraStreamStageStore {
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final String namespace;
    private final Duration ttl;

    public RedisAuroraStreamStageStore(StringRedisTemplate redis,
                                       ObjectMapper objectMapper,
                                       @Value("${inner-cosmos.aurora.stream.stage-namespace:inner-cosmos:aurora:stage:v1}") String namespace,
                                       @Value("${inner-cosmos.aurora.stream.stage-ttl:PT1M}") Duration ttl) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.namespace = namespace;
        this.ttl = ttl;
    }

    @Override
    public String stage(Long userId, ChatRequest request) {
        if (userId == null || request == null) return null;
        try {
            String token = UUID.randomUUID().toString().replace("-", "");
            redis.opsForValue().set(key(userId, token), objectMapper.writeValueAsString(request), ttl);
            return token;
        } catch (Exception error) {
            throw new IllegalStateException("Redis Aurora stream staging failed", error);
        }
    }

    @Override
    public ChatRequest consume(Long userId, String token) {
        if (userId == null || token == null || token.isBlank()) return null;
        try {
            String json = redis.opsForValue().getAndDelete(key(userId, token));
            return json == null ? null : objectMapper.readValue(json, ChatRequest.class);
        } catch (Exception error) {
            throw new IllegalStateException("Redis Aurora stream stage consume failed", error);
        }
    }

    private String key(Long userId, String token) {
        return namespace + ":user:" + userId + ":" + token;
    }
}
