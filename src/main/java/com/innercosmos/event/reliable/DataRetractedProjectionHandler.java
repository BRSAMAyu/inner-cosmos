package com.innercosmos.event.reliable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.service.RetractionDerivativeCleanupService;
import org.springframework.stereotype.Component;

/**
 * CP-39: local consumer for data.retracted.v1. Without a registered handler the outbox
 * worker retried every retraction notice to DEAD — retraction events, of all things, must
 * never silently die in a queue. Local consumption validates the payload against the v1
 * schema (a malformed payload is a poison message and fails loudly instead of propagating
 * garbage downstream) and records the inbox receipt, which is the audit proof that the
 * durable retraction notice was processed; remote projections consume it through their own
 * transport with this receipt as the shared idempotency anchor.
 *
 * <p>CP-15 (closing-checklist §2-11): after validation the consumer now also drives the
 * per-asset derivative cleanup via {@link RetractionDerivativeCleanupService} — the five
 * inventory classes (cache / object storage / export package / push copy / provider copy)
 * plus the capsule match-vector re-assertion. Each asset commits its own outcome row
 * (SUCCESS / FAILED / NOT_APPLICABLE + reason); a failing asset does not block the others,
 * and if anything fails the handler throws so the outbox retries the event. The executor
 * itself is idempotent per (event, asset), so a retry or dead-letter replay never deletes
 * twice.</p>
 */
@Component
public class DataRetractedProjectionHandler implements OutboxEventHandler {

    private final ObjectMapper objectMapper;
    private final RetractionDerivativeCleanupService derivativeCleanup;

    public DataRetractedProjectionHandler(ObjectMapper objectMapper,
                                          RetractionDerivativeCleanupService derivativeCleanup) {
        this.objectMapper = objectMapper;
        this.derivativeCleanup = derivativeCleanup;
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
        // CP-15 §2-11: per-inventory-asset derivative cleanup. Throws (after recording every
        // asset's outcome row) if any asset cleanup failed, so the outbox retries the event.
        derivativeCleanup.cleanForRetraction(new RetractionDerivativeCleanupService.RetractionCleanupCommand(
                event.eventId(),
                body.path("receiptId").asLong(),
                body.path("userId").asLong(),
                body.path("subjectType").asText(),
                body.path("subjectId").asLong(),
                body.path("derivativeType").asText(),
                body.path("action").asText()));
    }

    private static void require(JsonNode body, String field) {
        if (body.path(field).isMissingNode() || body.path(field).isNull()) {
            throw new IllegalStateException("data.retracted.v1 payload missing field: " + field);
        }
    }
}
