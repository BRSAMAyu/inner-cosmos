package com.innercosmos.service.metric;

import java.util.Set;

/**
 * Frozen metric-code registry (CP-03). The props allowlist is the structural P0 guard:
 * anything not listed here can never be persisted into tb_commercial_metric_event.
 */
public enum MetricCode {
    /** User finished a real dialog session (server-confirmed atomic FINISHED transition). */
    PRIVATE_DIALOG_COMPLETED("PRIVATE", true, Set.of("sessionId", "mode")),
    /** User explicitly confirmed a weekly review / memory settlement as useful. */
    VALUE_CONFIRMED_PRIVATE("PRIVATE", true, Set.of("scope")),
    /** A real person's slow letter actually departed (atomic SENT transition). */
    CONNECTED_REAL_SEND("CONNECTED", true, Set.of("threadId", "toUserId", "toCapsuleId")),
    /** User confirmed a qualified real-person exchange as valuable (context = letter thread). */
    VALUE_CONFIRMED_CONNECTED("CONNECTED", true, Set.of("threadId")),
    /** Safety rule hit recorded by SafetyService; compliance record, not user analytics. */
    SAFETY_INCIDENT("PLATFORM", false, Set.of("safetyEventId", "triggerScene")),
    /** Data-rights action completed (retraction receipt). */
    RIGHTS_ACTION_COMPLETED("PLATFORM", false, Set.of("receiptId", "subjectType", "affectedCount")),
    /** Payment captured, net of nothing yet — CP-45 emitter, contract ready. */
    PAYMENT_CAPTURED("PLATFORM", false, Set.of("orderId", "channel", "amountCents", "currency")),
    /** Refund settled against a captured payment — CP-45 emitter / backfill path. */
    REFUND_SETTLED("PLATFORM", false, Set.of("orderId", "channel", "amountCents", "currency"));

    public final String pathway;
    /** Whether the event is user-level analytics that must respect the analysis-consent gate. */
    public final boolean requiresAnalysisConsent;
    public final Set<String> allowedProps;

    MetricCode(String pathway, boolean requiresAnalysisConsent, Set<String> allowedProps) {
        this.pathway = pathway;
        this.requiresAnalysisConsent = requiresAnalysisConsent;
        this.allowedProps = allowedProps;
    }
}
