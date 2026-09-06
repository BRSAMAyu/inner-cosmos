package com.innercosmos.service.metric;

import com.innercosmos.entity.CommercialMetricEvent;
import java.time.Instant;
import java.util.Map;

/**
 * CP-03 metric event ingestion. All write paths enforce the blueprint §4 contracts:
 * server-confirmed facts only, UTC storage with Asia/Shanghai anchors, natural-key
 * dedup, test-account isolation, analysis-consent gate and the P0 props allowlist.
 */
public interface MetricEventService {

    /** Default analysis-consent policy version applied when a user has no explicit row. */
    String DEFAULT_CONSENT_VERSION = "AC-2026-09-v1";

    /**
     * Record one server-confirmed metric fact. Idempotent on the natural event key.
     *
     * @return the persisted row, or {@code null} when the fact was legitimately not
     *         recorded (duplicate, non-HUMAN account, declined analysis consent).
     */
    CommercialMetricEvent record(MetricCode code, Long userId, Instant occurredAt,
                                 String contextType, String contextId,
                                 String dimA, String dimB, Map<String, Object> props);

    /**
     * Anonymize every event of a user (account deletion): keep the rows so historical
     * denominators stay stable, drop the linkable identity and scrub props.
     *
     * @return number of rows anonymized.
     */
    int anonymizeUser(Long userId);

    /** Current effective analysis-consent status for a user (default GRANTED, v1). */
    boolean analysisConsentGranted(Long userId);
}
