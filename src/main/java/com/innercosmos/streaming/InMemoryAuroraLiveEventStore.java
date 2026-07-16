package com.innercosmos.streaming;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "inner-cosmos.aurora.stream.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryAuroraLiveEventStore implements AuroraLiveEventStore {
    private final Map<Long, List<AuroraLiveEvent>> events = new ConcurrentHashMap<>();
    private final Map<Long, Object> monitors = new ConcurrentHashMap<>();
    private final int maxLength;

    public InMemoryAuroraLiveEventStore(
            @Value("${inner-cosmos.aurora.stream.max-length:1024}") int maxLength) {
        this.maxLength = maxLength;
    }

    @Override
    public void publish(AuroraLiveEvent event) {
        Object monitor = monitors.computeIfAbsent(event.turnId(), ignored -> new Object());
        synchronized (monitor) {
            List<AuroraLiveEvent> turnEvents = events.computeIfAbsent(event.turnId(), ignored -> new ArrayList<>());
            turnEvents.add(event);
            if (turnEvents.size() > maxLength) turnEvents.subList(0, turnEvents.size() - maxLength).clear();
            monitor.notifyAll();
        }
    }

    @Override
    public List<AuroraLiveEvent> readAfter(Long userId, Long turnId, long afterSequence, Duration wait) {
        Object monitor = monitors.computeIfAbsent(turnId, ignored -> new Object());
        synchronized (monitor) {
            List<AuroraLiveEvent> found = select(userId, turnId, afterSequence);
            if (!found.isEmpty() || wait == null || wait.isZero() || wait.isNegative()) return found;
            try {
                monitor.wait(wait.toMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return List.of();
            }
            return select(userId, turnId, afterSequence);
        }
    }

    private List<AuroraLiveEvent> select(Long userId, Long turnId, long afterSequence) {
        return events.getOrDefault(turnId, List.of()).stream()
                .filter(event -> userId.equals(event.userId()) && event.sequence() > afterSequence)
                .toList();
    }
}
