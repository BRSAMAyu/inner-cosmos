package com.innercosmos.event.reliable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class DialogFinishedAuditHandler implements OutboxEventHandler {
    private final ObjectMapper objectMapper;

    public DialogFinishedAuditHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String eventType() {
        return DialogFinishedOutboxWriter.EVENT_TYPE;
    }

    @Override
    public String consumerName() {
        return "dialog-finished-audit-v1";
    }

    @Override
    public void handle(OutboxEvent event) {
        try {
            JsonNode payload = objectMapper.readTree(event.payload());
            if (!payload.hasNonNull("userId") || !payload.hasNonNull("sessionId")) {
                throw new IllegalArgumentException("Dialog-finished event is missing required identifiers");
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Dialog-finished event payload is invalid", e);
        }
    }
}
