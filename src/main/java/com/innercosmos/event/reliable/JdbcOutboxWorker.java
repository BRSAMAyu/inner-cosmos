package com.innercosmos.event.reliable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "inner-cosmos.events.outbox.enabled", havingValue = "true")
@ConditionalOnExpression("'${inner-cosmos.runtime.role:all}' == 'all' or '${inner-cosmos.runtime.role:all}' == 'worker'")
public class JdbcOutboxWorker {
    private static final Logger log = LoggerFactory.getLogger(JdbcOutboxWorker.class);
    private static final Duration LEASE = Duration.ofSeconds(30);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(5);
    private static final int MAX_ATTEMPTS = 5;

    private final JdbcOutboxRepository repository;
    private final Map<String, OutboxEventHandler> handlers;
    private final String workerId;

    public JdbcOutboxWorker(JdbcOutboxRepository repository, List<OutboxEventHandler> handlers) {
        this.repository = repository;
        this.handlers = new HashMap<>();
        for (OutboxEventHandler handler : handlers) {
            if (this.handlers.put(handler.eventType(), handler) != null) {
                throw new IllegalStateException("Duplicate outbox handler for " + handler.eventType());
            }
        }
        this.workerId = hostName() + ":" + UUID.randomUUID();
    }

    @Scheduled(fixedDelayString = "${inner-cosmos.events.outbox.poll-delay-ms:1000}")
    public void poll() {
        for (OutboxEvent event : repository.claim(workerId, 25, LEASE)) {
            OutboxEventHandler handler = handlers.get(event.eventType());
            if (handler == null) {
                repository.retry(event, new IllegalStateException("No handler for event type"), MAX_ATTEMPTS, RETRY_DELAY);
                continue;
            }
            try {
                repository.complete(event, handler);
            } catch (RuntimeException e) {
                log.warn("Outbox event {} failed on attempt {}", event.eventId(), event.attempts() + 1);
                repository.retry(event, e, MAX_ATTEMPTS, RETRY_DELAY);
            }
        }
    }

    private String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            return "worker";
        }
    }
}
