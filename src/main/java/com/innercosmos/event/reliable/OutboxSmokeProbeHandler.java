package com.innercosmos.event.reliable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Opt-in operational probe for the JDBC outbox delivery path.
 *
 * <p>The production image verifier needs to prove that a worker can claim an event, dispatch a
 * registered handler and atomically persist both the inbox receipt and the published status. A
 * real domain event is deliberately unsuitable for that check: it would require private user
 * fixtures and may call an external AI provider. This handler is disabled by default and has no
 * side effect beyond validating its synthetic payload. It is enabled only by the isolated image
 * smoke environment.</p>
 */
@Component
@ConditionalOnProperty(
        name = "inner-cosmos.events.outbox.smoke-probe-enabled",
        havingValue = "true")
public class OutboxSmokeProbeHandler implements OutboxEventHandler {
    public static final String EVENT_TYPE = "system.outbox-smoke-probe.v1";

    private final ObjectMapper objectMapper;

    public OutboxSmokeProbeHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public String consumerName() {
        return "outbox-smoke-probe-v1";
    }

    @Override
    public void handle(OutboxEvent event) {
        try {
            JsonNode payload = objectMapper.readTree(event.payload());
            if (payload == null
                    || !payload.path("probeId").isTextual()
                    || payload.path("probeId").asText().isBlank()) {
                throw new IllegalArgumentException("Outbox smoke probe requires a non-blank probeId");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Outbox smoke probe payload is invalid", e);
        }
    }
}
