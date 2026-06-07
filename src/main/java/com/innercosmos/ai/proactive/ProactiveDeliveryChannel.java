package com.innercosmos.ai.proactive;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE delivery channel for proactive push notifications.
 * Manages in-memory emitter registry per user.
 */
@Component
public class ProactiveDeliveryChannel {

    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId) {
        SseEmitter em = new SseEmitter(0L); // no timeout
        emitters.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(em);
        em.onCompletion(() -> {
            var set = emitters.get(userId);
            if (set != null) set.remove(em);
        });
        em.onTimeout(() -> {
            var set = emitters.get(userId);
            if (set != null) set.remove(em);
        });
        return em;
    }

    public void push(Long userId, String content, String type) {
        var set = emitters.get(userId);
        if (set == null || set.isEmpty()) {
            // offline: caller should log to tb_proactive_event_log with sent_at=null
            return;
        }
        for (SseEmitter em : Set.copyOf(set)) {
            try {
                em.send(SseEmitter.event()
                    .name("proactive")
                    .data(Map.of("type", type, "content", content, "ts", Instant.now().toString())));
            } catch (IOException e) {
                set.remove(em);
            }
        }
    }

    public boolean hasActiveEmitter(Long userId) {
        var set = emitters.get(userId);
        return set != null && !set.isEmpty();
    }
}