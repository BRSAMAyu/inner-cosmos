package com.innercosmos.ai.gateway;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * CP-17 call manifest: every real-provider egress records task, user, purpose, data class,
 * region, provider and the structured contract/filing version — the auditable counterpart
 * the gateway audit must match. The contract version is a closed {@link ModelContractVersion},
 * never free text: unknown provider labels are recorded as UNREGISTERED (the row survives as
 * audit truth; the contract metadata is never invented). Bounded in-memory ring (the durable
 * sink is the CP-40 observability pipeline); reads are for tests and ops spot-checks, never
 * for product features.
 */
@Component
public class GatewayCallLedger {

    public record CallRecord(LocalDateTime at, Long userId, String moduleName, String provider,
                             String purpose, String dataClass, String region,
                             ModelContractVersion contractVersion, String outcome) {
    }

    private static final int CAPACITY = 200;
    private final Deque<CallRecord> records = new ArrayDeque<>();

    public void record(Long userId, String moduleName, String provider, String outcome) {
        synchronized (records) {
            if (records.size() == CAPACITY) {
                records.removeFirst();
            }
            records.addLast(new CallRecord(LocalDateTime.now(ZoneOffset.UTC), userId, moduleName,
                    provider, "AI_PROVIDER_EGRESS", "USER_CONTENT", "CN",
                    ModelContractVersion.forProvider(provider), outcome));
        }
    }

    public List<CallRecord> recent(int limit) {
        int bounded = Math.max(1, Math.min(CAPACITY, limit));
        synchronized (records) {
            return List.copyOf(records).subList(Math.max(0, records.size() - bounded), records.size());
        }
    }
}
