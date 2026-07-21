package com.innercosmos.event.reliable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.service.ClaimCandidateService;
import com.innercosmos.service.EmotionTimelineService;
import com.innercosmos.service.MemoryService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Durable projection for a completed Aurora dialog.
 *
 * <p>{@link JdbcOutboxRepository#complete(OutboxEvent, OutboxEventHandler)} invokes this handler
 * in the same database transaction that creates the inbox receipt and publishes the outbox row.
 * Any exception therefore rolls back both the derived records and the receipt, so the worker can
 * retry without acknowledging a partially projected conversation.</p>
 */
@Component
public class DialogFinishedProjectionHandler implements OutboxEventHandler {
    private final ObjectMapper objectMapper;
    private final MemoryService memoryService;
    private final ClaimCandidateService claimCandidateService;
    private final EmotionTimelineService emotionTimelineService;

    public DialogFinishedProjectionHandler(ObjectMapper objectMapper,
                                           MemoryService memoryService,
                                           ClaimCandidateService claimCandidateService,
                                           EmotionTimelineService emotionTimelineService) {
        this.objectMapper = objectMapper;
        this.memoryService = memoryService;
        this.claimCandidateService = claimCandidateService;
        this.emotionTimelineService = emotionTimelineService;
    }

    @Override
    public String eventType() {
        return DialogFinishedOutboxWriter.EVENT_TYPE;
    }

    @Override
    public String consumerName() {
        return "dialog-finished-projection-v1";
    }

    @Override
    public void handle(OutboxEvent event) {
        Projection projection = parse(event.payload());
        memoryService.extractFromSession(projection.userId(), projection.sessionId());
        claimCandidateService.stageForSession(projection.userId(), projection.sessionId());
        emotionTimelineService.aggregateFromTraces(projection.userId(), LocalDate.now());
    }

    private Projection parse(String payload) {
        try {
            JsonNode json = objectMapper.readTree(payload);
            if (json == null || !json.hasNonNull("userId") || !json.hasNonNull("sessionId")
                    || !json.path("userId").canConvertToLong() || !json.path("sessionId").canConvertToLong()) {
                throw new IllegalArgumentException("Dialog-finished event is missing valid identifiers");
            }
            long userId = json.path("userId").longValue();
            long sessionId = json.path("sessionId").longValue();
            if (userId <= 0 || sessionId <= 0) {
                throw new IllegalArgumentException("Dialog-finished identifiers must be positive");
            }
            return new Projection(userId, sessionId);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Dialog-finished event payload is invalid", e);
        }
    }

    private record Projection(Long userId, Long sessionId) {}
}
