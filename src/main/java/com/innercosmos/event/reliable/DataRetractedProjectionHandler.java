package com.innercosmos.event.reliable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * CP-39: local consumer for data.retracted.v1. Without a registered handler the outbox
 * worker retried every retraction notice to DEAD — retraction events, of all things, must
 * never silently die in a queue. Local consumption validates the payload against the v1
 * schema (a malformed payload is a poison message and fails loudly instead of propagating
 * garbage downstream) and records the inbox receipt, which is the audit proof that the
 * durable retraction notice was processed; remote projections consume it through their own
 * transport with this receipt as the shared idempotency anchor.
 */
@Component
public class DataRetractedProjectionHandler implements OutboxEventHandler {

    private final ObjectMapper objectMapper;

    public DataRetractedProjectionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String eventType() {
        return DataRetractedOutboxWriter.EVENT_TYPE;
    }

    @Override
    public String consumerName() {
        return "data-retraction-projection";
    }

    @Override
    public void handle(OutboxEvent event) {
        JsonNode body;
        try {
            body = objectMapper.readTree(event.payload());
        } catch (Exception e) {
            throw new IllegalStateException("data.retracted.v1 payload is not valid JSON", e);
        }
        require(body, "receiptId");
        require(body, "userId");
        require(body, "subjectType");
        require(body, "subjectId");
        require(body, "derivativeType");
        require(body, "action");
        if (event.schemaVersion() != DataRetractedOutboxWriter.SCHEMA_VERSION) {
            throw new IllegalStateException("data.retracted.v1 unsupported schema version: "
                    + event.schemaVersion());
        }
    }

    private static void require(JsonNode body, String field) {
        if (body.path(field).isMissingNode() || body.path(field).isNull()) {
            throw new IllegalStateException("data.retracted.v1 payload missing field: " + field);
        }
    }
}
