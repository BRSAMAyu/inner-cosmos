package com.innercosmos.safety;

import com.innercosmos.entity.CrisisIntervention;
import com.innercosmos.entity.UserRiskState;
import java.util.List;

/**
 * CP-20 durable risk continuity. The per-session rolling view stays in
 * {@link SessionRiskAggregator}; THIS service owns the cross-session, cross-Pod picture with
 * explicit decay, the auditable escalation ledger and the G-SAFE feed. Explicit crisis
 * statements (HIGH) never raise the durable level — they trigger the emergency protocol row
 * immediately, keeping "level" an accumulation signal rather than a panic label.
 */
public interface CrisisContinuityService {

    record Observation(UserRiskState state, CrisisIntervention intervention) {
    }

    /**
     * Feed one safety observation into the durable user state. Negation/past-tense and
     * third-party-quote text is contextualized exactly like the session aggregator
     * (never a lift unless the level itself is HIGH).
     */
    Observation observe(Long userId, Long safetyEventId, String riskLevel, String text);

    /** Current durable state (creates a NONE baseline row on first read). */
    UserRiskState statusOf(Long userId);

    /** Escalation ledger for one user, newest first. */
    List<CrisisIntervention> interventions(Long userId, int limit);

    /** Owner-visible transparency view: level, last observed time, support-resource hint. */
    record OwnerStatus(String level, String observedAt, String explanation, boolean supportVisible) {
    }

    OwnerStatus ownerStatus(Long userId);
}
