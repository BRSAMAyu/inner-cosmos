package com.innercosmos.service.metric;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.entity.CommercialMetricRollup;
import com.innercosmos.mapper.CommercialMetricEventMapper;
import com.innercosmos.mapper.CommercialMetricEventMapper.MetricProjection;
import com.innercosmos.mapper.CommercialMetricRollupMapper;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

@Service
public class CommercialMetricQueryServiceImpl implements CommercialMetricQueryService {

    /** K1 connection confirmations may arrive up to 14 days after the anchor week. */
    static final int CONNECTED_CONFIRMATION_WINDOW_DAYS = 14;
    /** K2 observation window: day 28..34 after the activation day t0. */
    static final int K2_WINDOW_START_DAY = 28;
    static final int K2_WINDOW_END_DAY = 34;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final CommercialMetricEventMapper eventMapper;
    private final CommercialMetricRollupMapper rollupMapper;
    private final Clock clock;

    public CommercialMetricQueryServiceImpl(CommercialMetricEventMapper eventMapper,
                                            CommercialMetricRollupMapper rollupMapper,
                                            Clock clock) {
        this.eventMapper = eventMapper;
        this.rollupMapper = rollupMapper;
        this.clock = clock;
    }

    @Override
    public K1WeeklyReport k1Weekly(String anchorWeek) {
        LocalDate weekStart = weekStart(anchorWeek);
        LocalDate weekEnd = weekStart.plusDays(6);
        LocalDate today = LocalDate.now(clock.withZone(java.time.ZoneId.of("Asia/Shanghai")));

        List<MetricProjection> dialogs = eventMapper.projectRange(
                MetricCode.PRIVATE_DIALOG_COMPLETED.name(), anchorWeek, anchorWeek);
        List<MetricProjection> confirms = eventMapper.projectRange(
                MetricCode.VALUE_CONFIRMED_PRIVATE.name(), anchorWeek, anchorWeek);

        // Denominator: every user with >=1 server-confirmed completed dialog in the anchor week.
        Map<String, Set<String>> dialogsByUser = new HashMap<>();
        for (MetricProjection row : dialogs) {
            dialogsByUser.computeIfAbsent(userKey(row), k -> new HashSet<>()).add(row.getAnchorDay());
        }
        // Numerator: confirmed useful in-week AND a core use on a DIFFERENT day of the same week.
        Set<String> confirmedUsers = new HashSet<>();
        for (MetricProjection row : confirms) {
            confirmedUsers.add(userKey(row));
        }
        long numerator = 0;
        for (Map.Entry<String, Set<String>> entry : dialogsByUser.entrySet()) {
            if (!confirmedUsers.contains(entry.getKey())) {
                continue;
            }
            // The confirmation itself proves an in-week core use; "另一天" requires a second day.
            if (entry.getValue().size() >= 2) {
                numerator++;
            }
        }
        // K1 private matures when the week closes (values can no longer be added late); the
        // blueprint's 14-day maturation applies to the connected pathway below.
        boolean privateMatured = today.isAfter(weekEnd);

        // Connected pathway: a qualified exchange is a letter thread with >=2 distinct real
        // senders in the anchor week; confirmed when BOTH senders confirm within 14 days
        // after the anchor week starts (confirmation context = thread id).
        List<MetricProjection> sends = eventMapper.projectRange(
                MetricCode.CONNECTED_REAL_SEND.name(), anchorWeek, anchorWeek);
        Map<String, Set<String>> sendersByThread = new HashMap<>();
        for (MetricProjection row : sends) {
            if (row.getContextId() == null || row.getUserId() == null) {
                continue;
            }
            sendersByThread.computeIfAbsent(row.getContextId(), k -> new HashSet<>())
                    .add(String.valueOf(row.getUserId()));
        }
        Set<String> qualifiedThreads = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : sendersByThread.entrySet()) {
            if (entry.getValue().size() >= 2) {
                qualifiedThreads.add(entry.getKey());
            }
        }
        String confirmFromDay = weekStart.toString();
        String confirmToDay = weekStart.plusDays(CONNECTED_CONFIRMATION_WINDOW_DAYS).toString();
        List<MetricProjection> connectedConfirms = eventMapper.projectRange(
                MetricCode.VALUE_CONFIRMED_CONNECTED.name(), anchorWeek, shiftedWeek(anchorWeek, 2));
        Map<String, Set<String>> confirmedUsersByThread = new HashMap<>();
        for (MetricProjection row : connectedConfirms) {
            if (row.getContextId() == null || row.getUserId() == null) {
                continue;
            }
            if (row.getAnchorDay().compareTo(confirmFromDay) < 0
                    || row.getAnchorDay().compareTo(confirmToDay) > 0) {
                continue;
            }
            confirmedUsersByThread.computeIfAbsent(row.getContextId(), k -> new HashSet<>())
                    .add(String.valueOf(row.getUserId()));
        }
        long confirmedExchanges = 0;
        for (String thread : qualifiedThreads) {
            Set<String> required = sendersByThread.get(thread);
            Set<String> confirmed = confirmedUsersByThread.getOrDefault(thread, Set.of());
            if (confirmed.containsAll(required)) {
                confirmedExchanges++;
            }
        }
        boolean connectedMatured = today.isAfter(weekStart.plusDays(
                CONNECTED_CONFIRMATION_WINDOW_DAYS + 6));

        long denominator = dialogsByUser.size()
                + rollupCount(MetricCode.PRIVATE_DIALOG_COMPLETED.name(), anchorWeek);
        return new K1WeeklyReport(anchorWeek, denominator, numerator, privateMatured,
                qualifiedThreads.size(), confirmedExchanges, connectedMatured);
    }

    @Override
    public List<K2CohortReport> k2Cohorts(boolean includeImmature) {
        LocalDate today = LocalDate.now(clock.withZone(java.time.ZoneId.of("Asia/Shanghai")));
        List<MetricProjection> dialogs = eventMapper.projectRange(
                MetricCode.PRIVATE_DIALOG_COMPLETED.name(), "0000-W01", "9999-W53");
        // t0 = first completed-dialog day per user (activation), bucketed by activation week.
        Map<String, String> activationDayByUser = new HashMap<>();
        Map<String, String> activationWeekByUser = new HashMap<>();
        for (MetricProjection row : dialogs) {
            String key = userKey(row);
            String day = row.getAnchorDay();
            if (row.getUserId() == null) {
                continue; // anonymized users keep historical denominators, not cohort tracking
            }
            if (activationDayByUser.getOrDefault(key, "9999-12-31").compareTo(day) > 0) {
                activationDayByUser.put(key, day);
                activationWeekByUser.put(key, row.getAnchorWeek());
            }
        }
        List<MetricProjection> confirms = eventMapper.projectRange(
                MetricCode.VALUE_CONFIRMED_PRIVATE.name(), "0000-W01", "9999-W53");
        // Core-value behavior days per user: completed dialogs or explicit value confirmations.
        Map<String, Set<String>> valueDaysByUser = new HashMap<>();
        for (MetricProjection row : dialogs) {
            if (row.getUserId() != null) {
                valueDaysByUser.computeIfAbsent(userKey(row), k -> new HashSet<>())
                        .add(row.getAnchorDay());
            }
        }
        for (MetricProjection row : confirms) {
            if (row.getUserId() != null) {
                valueDaysByUser.computeIfAbsent(userKey(row), k -> new HashSet<>())
                        .add(row.getAnchorDay());
            }
        }

        Map<String, long[]> byCohort = new TreeMap<>();
        for (Map.Entry<String, String> entry : activationWeekByUser.entrySet()) {
            String user = entry.getKey();
            LocalDate t0 = LocalDate.parse(activationDayByUser.get(user));
            LocalDate windowStart = t0.plusDays(K2_WINDOW_START_DAY);
            LocalDate windowEnd = t0.plusDays(K2_WINDOW_END_DAY);
            boolean matured = !today.isBefore(windowEnd);
            if (!matured && !includeImmature) {
                continue;
            }
            long[] counts = byCohort.computeIfAbsent(entry.getValue(), k -> new long[2]);
            counts[0]++;
            Set<String> days = valueDaysByUser.getOrDefault(user, Set.of());
            for (LocalDate d = windowStart; !d.isAfter(windowEnd); d = d.plusDays(1)) {
                if (days.contains(d.toString())) {
                    counts[1]++;
                    break;
                }
            }
        }
        List<K2CohortReport> reports = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : byCohort.entrySet()) {
            String week = entry.getKey();
            LocalDate weekStart = weekStart(week);
            boolean matured = !today.isBefore(weekStart.plusDays(K2_WINDOW_END_DAY));
            // Deleted users stay in their original cohort denominator, never in the numerator.
            long observed = entry.getValue()[0] + rollupCount(
                    com.innercosmos.service.metric.MetricEventServiceImpl.K2_ACTIVATION_ROLLUP, week);
            reports.add(new K2CohortReport(week, observed, entry.getValue()[1], matured));
        }
        return reports;
    }

    @Override
    public GSafeWeeklyReport gSafeWeekly(String anchorWeek) {
        List<MetricProjection> incidents = eventMapper.projectRange(
                MetricCode.SAFETY_INCIDENT.name(), anchorWeek, anchorWeek);
        long coreSessions = eventMapper.projectRange(
                MetricCode.PRIVATE_DIALOG_COMPLETED.name(), anchorWeek, anchorWeek).size()
                + rollupCount(MetricCode.PRIVATE_DIALOG_COMPLETED.name(), anchorWeek);
        Map<String, Long> bySeverity = new TreeMap<>();
        for (MetricProjection row : incidents) {
            String severity = row.getDimA() == null ? "UNKNOWN" : row.getDimA();
            bySeverity.merge(severity, 1L, Long::sum);
        }
        long anonymizedIncidents = rollupCount(MetricCode.SAFETY_INCIDENT.name(), anchorWeek);
        if (anonymizedIncidents > 0) {
            bySeverity.merge("ANONYMIZED", anonymizedIncidents, Long::sum);
        }
        return new GSafeWeeklyReport(anchorWeek, coreSessions,
                incidents.size() + anonymizedIncidents, bySeverity);
    }

    @Override
    public GTrustWeeklyReport gTrustWeekly(String anchorWeek) {
        List<MetricProjection> actions = eventMapper.projectRange(
                MetricCode.RIGHTS_ACTION_COMPLETED.name(), anchorWeek, anchorWeek);
        Map<String, Long> byAction = new LinkedHashMap<>();
        long affected = 0;
        for (MetricProjection row : actions) {
            byAction.merge(row.getDimA() == null ? "UNKNOWN" : row.getDimA(), 1L, Long::sum);
            affected += propLong(row, "affectedCount");
        }
        long anonymizedActions = rollupCount(MetricCode.RIGHTS_ACTION_COMPLETED.name(), anchorWeek);
        if (anonymizedActions > 0) {
            byAction.merge("ANONYMIZED", anonymizedActions, Long::sum);
            affected += rollupAffected(MetricCode.RIGHTS_ACTION_COMPLETED.name(), anchorWeek);
        }
        return new GTrustWeeklyReport(anchorWeek, byAction, affected);
    }

    @Override
    public RelationQualityReport relationQuality(String anchorWeek) {
        // CP-59: threads active in the anchor week, from CONNECTED_REAL_SEND events
        // (context = LETTER_THREAD id). A "round trip" is order-insensitive and
        // human-checkable: min(sends by side A, sends by side B) per thread — 回轮深度.
        List<MetricProjection> sends = eventMapper.projectRange(
                MetricCode.CONNECTED_REAL_SEND.name(), anchorWeek, anchorWeek);
        Map<String, Map<String, Long>> sendCountsByThread = new HashMap<>();
        for (MetricProjection row : sends) {
            if (row.getContextId() == null || row.getUserId() == null) {
                continue;
            }
            sendCountsByThread
                    .computeIfAbsent(row.getContextId(), k -> new HashMap<>())
                    .merge(String.valueOf(row.getUserId()), 1L, Long::sum);
        }
        long active = sendCountsByThread.size();
        long bidirectional = 0;
        long threeRounds = 0;
        for (Map<String, Long> counts : sendCountsByThread.values()) {
            if (counts.size() < 2) {
                continue; // one-sided sends never form a round trip
            }
            bidirectional++;
            long min = Long.MAX_VALUE;
            for (long count : counts.values()) {
                min = Math.min(min, count);
            }
            if (min >= 3) {
                threeRounds++;
            }
        }
        double share = active == 0 ? 0d : threeRounds * 1d / active;
        double[] ci = wilson95(threeRounds, active);
        // Harassment incidents share the week's SAFETY_INCIDENT events (same source
        // G-SAFE reads: live rows + anonymized rollups) so the two reports never
        // disagree about what an incident is.
        long incidents = eventMapper.projectRange(
                MetricCode.SAFETY_INCIDENT.name(), anchorWeek, anchorWeek).size()
                + rollupCount(MetricCode.SAFETY_INCIDENT.name(), anchorWeek);
        double perThread = active == 0 ? 0d : incidents * 1d / active;
        return new RelationQualityReport(anchorWeek, active, bidirectional, threeRounds,
                share, ci[0], ci[1], incidents, perThread);
    }

    private static double[] wilson95(long successes, long trials) {
        if (trials == 0) {
            return new double[]{0d, 0d};
        }
        double z = 1.959963984540054d;
        double p = successes * 1d / trials;
        double denominator = 1 + z * z / trials;
        double centre = p + z * z / (2 * trials);
        double margin = z * Math.sqrt(p * (1 - p) / trials + z * z / (4 * trials * trials));
        return new double[]{Math.max(0d, (centre - margin) / denominator),
                Math.min(1d, (centre + margin) / denominator)};
    }

    private long rollupCount(String metricCode, String anchorWeek) {
        CommercialMetricRollup rollup = rollupMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CommercialMetricRollup>()
                        .eq("metric_code", metricCode).eq("anchor_week", anchorWeek));
        return rollup == null ? 0L : rollup.anonymizedCount;
    }

    private long rollupAffected(String metricCode, String anchorWeek) {
        CommercialMetricRollup rollup = rollupMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CommercialMetricRollup>()
                        .eq("metric_code", metricCode).eq("anchor_week", anchorWeek));
        return rollup == null ? 0L : rollup.affectedTotal;
    }

    /** Monday of the ISO week encoded as {@code yyyy-Www}. Jan 4 is always in ISO week 1. */
    static LocalDate weekStart(String anchorWeek) {
        String[] parts = anchorWeek.split("-W");
        int year = Integer.parseInt(parts[0]);
        int week = Integer.parseInt(parts[1]);
        LocalDate jan4 = LocalDate.of(year, 1, 4);
        return jan4.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(week - 1L);
    }

    static String shiftedWeek(String anchorWeek, int plusWeeks) {
        LocalDate start = weekStart(anchorWeek).plusWeeks(plusWeeks);
        return start.get(java.time.temporal.WeekFields.ISO.weekBasedYear())
                + "-W" + String.format("%02d", start.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear()));
    }

    /** Live rows always carry a user id (deleted accounts leave only rollup counters). */
    private static String userKey(MetricProjection row) {
        return "u" + row.getUserId();
    }

    private static long propLong(MetricProjection row, String key) {
        try {
            JsonNode node = JSON.readTree(row.getProps() == null ? "{}" : row.getProps());
            return node.hasNonNull(key) ? node.get(key).asLong(0L) : 0L;
        } catch (Exception malformed) {
            return 0L;
        }
    }
}
