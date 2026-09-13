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

    /**
     * CP-59 relation quality for one anchor week: the share of active letter threads
     * with at least three bidirectional round trips (双向往来 ≥3 轮), reported next to
     * the harassment-incident rate — the two numbers the community-health review reads
     * together (不以热度催回复：回轮深度才是关系质量，举报率是并列守门).
     */
    RelationQualityReport relationQuality(String anchorWeek);

    record RelationQualityReport(
            String anchorWeek,
            long activeThreads,
            long bidirectionalThreads,
            long threadsWithThreeRoundTrips,
            double threeRoundTripShare,
            /** Wilson 95% interval over thread units (one unit per thread). */
            double shareCi95Low,
            double shareCi95High,
            long harassmentIncidents,
            double harassmentPerActiveThread) {
    }

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
