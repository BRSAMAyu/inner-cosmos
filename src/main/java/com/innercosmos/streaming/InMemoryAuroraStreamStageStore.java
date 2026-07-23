package com.innercosmos.streaming;

import com.innercosmos.common.ErrorCode;
import com.innercosmos.dto.ChatRequest;
import com.innercosmos.exception.BusinessException;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "inner-cosmos.aurora.stream.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryAuroraStreamStageStore implements AuroraStreamStageStore {
    private final Map<String, StagedStream> stages = new ConcurrentHashMap<>();
    private final long ttlMillis;
    private final int maxEntries;
    // Gemini audit 2.8 (PARTIAL/P3): the report's "unbounded leakage" framing overstated things --
    // this store is maxEntries-bounded and Redis-disabled/dev-only -- but the actual gap was real:
    // purgeExpired() only ever ran inline from stage(), so a token that expired and was never
    // followed by *another* stage() call (caller abandons the flow, or this no-Redis path simply
    // goes idle) sat resident until unrelated traffic happened to trigger a fresh stage(). A
    // scheduled sweep plus an injectable Clock (not raw System.currentTimeMillis()) makes idle
    // expiry both real and independently testable, and @PreDestroy guarantees no residue survives
    // shutdown either.
    private Clock clock = Clock.systemUTC();

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
        stages.put(token, new StagedStream(userId, request, clock.millis() + ttlMillis));
        return token;
    }

    @Override
    public ChatRequest consume(Long userId, String token) {
        if (userId == null || token == null || token.isBlank()) return null;
        StagedStream staged = stages.get(token);
        if (staged == null || staged.expiresAt <= clock.millis()) {
            if (staged != null) stages.remove(token, staged);
            return null;
        }
        if (!userId.equals(staged.userId)) return null;
        return stages.remove(token, staged) ? staged.request : null;
    }

    /** Test-only: swap in a fixed clock so idle-expiry and the scheduled sweep are deterministic. */
    void useClock(Clock testClock) {
        this.clock = testClock;
    }

    /** Test-only: current resident count, independent of any stage()/consume() side effect. */
    int size() {
        return stages.size();
    }

    @Scheduled(fixedDelayString = "${inner-cosmos.aurora.stream.stage-cleanup-delay-ms:60000}")
    void cleanupExpired() {
        purgeExpired();
    }

    @PreDestroy
    void clear() {
        stages.clear();
    }

    private void purgeExpired() {
        long now = clock.millis();
        stages.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
    }

    private record StagedStream(Long userId, ChatRequest request, long expiresAt) {}
}
