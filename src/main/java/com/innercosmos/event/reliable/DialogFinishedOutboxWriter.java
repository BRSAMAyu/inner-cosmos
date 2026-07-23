package com.innercosmos.event.reliable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.event.DialogFinishedEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "inner-cosmos.events.outbox.enabled", havingValue = "true")
public class DialogFinishedOutboxWriter {
    public static final String EVENT_TYPE = "dialog.finished.v1";

    private final JdbcOutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final OutboxTraceContext traceContext;

    public DialogFinishedOutboxWriter(JdbcOutboxRepository repository, ObjectMapper objectMapper,
                                      OutboxTraceContext traceContext) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.traceContext = traceContext;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onDialogFinished(DialogFinishedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "userId", event.userId,
                    "sessionId", event.sessionId));
            repository.append(
                    UUID.randomUUID(),
                    "dialog-session:" + event.sessionId + ":finished:v1",
                    "dialog-session",
                    event.sessionId.toString(),
                    EVENT_TYPE,
                    1,
                    payload,
                    traceContext.capture());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize dialog-finished outbox payload", e);
        }
    }
}
