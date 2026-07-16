package com.innercosmos.streaming;

import com.innercosmos.common.ErrorCode;
import com.innercosmos.dto.ChatRequest;
import com.innercosmos.exception.BusinessException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "inner-cosmos.aurora.stream.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryAuroraStreamStageStore implements AuroraStreamStageStore {
    private final Map<String, StagedStream> stages = new ConcurrentHashMap<>();
    private final long ttlMillis;
    private final int maxEntries;

    public InMemoryAuroraStreamStageStore(
            @Value("${inner-cosmos.aurora.stream.stage-ttl:PT1M}") Duration ttl,
            @Value("${inner-cosmos.aurora.stream.stage-max-entries:1024}") int maxEntries) {
        this.ttlMillis = ttl.toMillis();
        this.maxEntries = maxEntries;
    }

    @Override
    public String stage(Long userId, ChatRequest request) {
        purgeExpired();
        if (stages.size() >= maxEntries) {
            throw new BusinessException(ErrorCode.CONFLICT, "too many pending stream stages");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        stages.put(token, new StagedStream(userId, request, System.currentTimeMillis() + ttlMillis));
        return token;
    }

    @Override
    public ChatRequest consume(Long userId, String token) {
        if (userId == null || token == null || token.isBlank()) return null;
        StagedStream staged = stages.get(token);
        if (staged == null || staged.expiresAt <= System.currentTimeMillis()) {
            if (staged != null) stages.remove(token, staged);
            return null;
        }
        if (!userId.equals(staged.userId)) return null;
        return stages.remove(token, staged) ? staged.request : null;
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        stages.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
    }

    private record StagedStream(Long userId, ChatRequest request, long expiresAt) {}
}
