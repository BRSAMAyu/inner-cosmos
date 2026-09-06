package com.innercosmos.service.metric;

import java.util.List;

/**
 * CP-03 metric read model. Each report type mirrors one contract in
 * docs/commercialization/ledger/metric-dictionary.md and is computed from the raw
 * event store so a synthetic sample is human-checkable end to end.
 */
public interface CommercialMetricQueryService {

    /** K1 weekly confirmed-value users (private + connected pathways, reported separately). */
    K1WeeklyReport k1Weekly(String anchorWeek);

    /** K2 D30-window value retention, one row per matured (or explicitly immature) activation week. */
    List<K2CohortReport> k2Cohorts(boolean includeImmature);

    /** G-SAFE weekly incident counts with per-thousand-core-session denominators. */
    GSafeWeeklyReport gSafeWeekly(String anchorWeek);

    /** G-TRUST weekly rights-action counts (promise-window SLA lands with CP-15). */
    GTrustWeeklyReport gTrustWeekly(String anchorWeek);

    record K1WeeklyReport(
            String anchorWeek,
            long privateDenominator,
            long privateNumerator,
            boolean privateMatured,
            long connectedQualifiedExchanges,
            long connectedConfirmedExchanges,
            boolean connectedMatured) {
    }

    record K2CohortReport(
            String activationWeek,
            long observedUsers,
            long retainedUsers,
            boolean matured) {
    }

    record GSafeWeeklyReport(
            String anchorWeek,
            long coreSessions,
            long incidentsTotal,
            java.util.Map<String, Long> incidentsBySeverity) {

        public double incidentsPerThousandCoreSessions() {
            return coreSessions == 0 ? 0d : incidentsTotal * 1000d / coreSessions;
        }
    }

    record GTrustWeeklyReport(
            String anchorWeek,
            java.util.Map<String, Long> actionsByType,
            long affectedRecordsTotal) {
    }
}
