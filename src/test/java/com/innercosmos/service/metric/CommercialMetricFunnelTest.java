package com.innercosmos.service.metric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.innercosmos.entity.AnalysisConsent;
import com.innercosmos.entity.CommercialMetricEvent;
import com.innercosmos.entity.User;
import com.innercosmos.mapper.AnalysisConsentMapper;
import com.innercosmos.mapper.CommercialMetricEventMapper;
import com.innercosmos.mapper.UserMapper;
import com.innercosmos.service.metric.CommercialMetricQueryService.K1WeeklyReport;
import com.innercosmos.service.metric.CommercialMetricQueryService.K2CohortReport;
import com.innercosmos.service.metric.CommercialMetricQueryService.GSafeWeeklyReport;
import com.innercosmos.service.metric.CommercialMetricQueryService.GTrustWeeklyReport;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * CP-03 acceptance: a hand-checkable synthetic sample exercising the whole funnel —
 * duplicates, offline/late ingestion, cross-week UTC/Shanghai boundaries, consent refusal,
 * test-account isolation, account-deletion rollups, refunds and non-response — with every
 * expected number derived by hand from the blueprint §4.1 definitions, not from the code.
 */
@SpringBootTest
class CommercialMetricFunnelTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Autowired
    private MetricEventService metricEventService;
    @Autowired
    private CommercialMetricQueryService queryService;
    @Autowired
    private CommercialMetricEventMapper eventMapper;
    @Autowired
    private AnalysisConsentMapper consentMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private Clock clock;

    private User human(String username) {
        User user = new User();
        user.username = username;
        user.passwordHash = "x";
        user.nickname = username;
        user.role = "USER";
        user.status = "ACTIVE";
        user.accountKind = "HUMAN";
        userMapper.insert(user);
        return user;
    }

    private static String weekOf(LocalDate date) {
        return date.get(WeekFields.ISO.weekBasedYear())
                + "-W" + String.format("%02d", date.get(WeekFields.ISO.weekOfWeekBasedYear()));
    }

    /** UTC instant at a given Shanghai local date/time. */
    private static java.time.Instant atShanghai(LocalDate day, int hour, int minute, int second) {
        return day.atTime(hour, minute, second).atZone(SHANGHAI).toInstant();
    }

    private void dialog(User user, LocalDate day, long sessionId) {
        metricEventService.record(MetricCode.PRIVATE_DIALOG_COMPLETED, user.id,
                atShanghai(day, 20, 0, 0), "DIALOG_SESSION", String.valueOf(sessionId),
                null, null, Map.of("sessionId", String.valueOf(sessionId)));
    }

    private void confirmPrivate(User user, LocalDate day) {
        metricEventService.record(MetricCode.VALUE_CONFIRMED_PRIVATE, user.id,
                atShanghai(day, 21, 0, 0), null, null, null, null,
                Map.of("scope", "PRIVATE_REVIEW"));
    }

    private void letterSend(User sender, LocalDate day, long threadId) {
        Map<String, Object> props = new HashMap<>();
        props.put("threadId", String.valueOf(threadId));
        metricEventService.record(MetricCode.CONNECTED_REAL_SEND, sender.id,
                atShanghai(day, 21, 30, 0), "LETTER_THREAD", String.valueOf(threadId),
                null, null, props);
    }

    private void confirmConnection(User user, LocalDate day, long threadId) {
        metricEventService.record(MetricCode.VALUE_CONFIRMED_CONNECTED, user.id,
                atShanghai(day, 22, 0, 0), "LETTER_THREAD", String.valueOf(threadId),
                null, null, Map.of("threadId", String.valueOf(threadId)));
    }

    @Test
    void fullFunnelOnSyntheticSample() {
        LocalDate today = LocalDate.now(clock.withZone(SHANGHAI));
        // A fully matured anchor week: Monday of 5 weeks ago (t0+34 always in the past).
        LocalDate pastMonday = today.with(new TemporalAdjustersPrevOrSameMonday())
                .minusWeeks(5);
        String pastWeek = weekOf(pastMonday.plusDays(3));

        User u1 = human("cp03-u1-" + System.nanoTime());
        User u2 = human("cp03-u2-" + System.nanoTime());
        User u3 = human("cp03-u3-" + System.nanoTime());
        User u4 = human("cp03-u4-" + System.nanoTime());
        User u5 = human("cp03-u5-" + System.nanoTime());
        User u6 = human("cp03-u6-" + System.nanoTime());
        User u7 = human("cp03-u7-" + System.nanoTime());
        User sandbox = new User();
        sandbox.username = "cp03-sb-" + System.nanoTime();
        sandbox.passwordHash = "x";
        sandbox.role = "USER";
        sandbox.status = "ACTIVE";
        sandbox.accountKind = "SANDBOX";
        userMapper.insert(sandbox);
        User declined = human("cp03-decl-" + System.nanoTime());
        AnalysisConsent refusal = new AnalysisConsent();
        refusal.userId = declined.id;
        refusal.status = "DECLINED";
        refusal.consentVersion = MetricEventService.DEFAULT_CONSENT_VERSION;
        consentMapper.insert(refusal);

        long s = 900_000_000L; // synthetic session/thread ids, unique per run

        // Baseline snapshot: other test classes share this H2 database and may have written
        // events into the same relative weeks, so every assertion below checks deltas.
        K1WeeklyReport k1Before = queryService.k1Weekly(pastWeek);
        GSafeWeeklyReport gSafeBefore = queryService.gSafeWeekly(pastWeek);
        GTrustWeeklyReport gTrustBefore = queryService.gTrustWeekly(pastWeek);
        K2CohortReport cohortBefore = queryService.k2Cohorts(true).stream()
                .filter(c -> pastWeek.equals(c.activationWeek())).findFirst()
                .orElse(new K2CohortReport(pastWeek, 0, 0, false));

        // --- K1 private: denominator 4, numerator 1 (u1 only: confirm + two distinct days).
        dialog(u1, pastMonday, s + 1);            // Monday
        dialog(u1, pastMonday.plusDays(2), s + 2); // Wednesday
        confirmPrivate(u1, pastMonday);
        dialog(u2, pastMonday.plusDays(1), s + 3); // Tuesday, confirm same single day -> not K1
        confirmPrivate(u2, pastMonday.plusDays(1));
        dialog(u3, pastMonday.plusDays(3), s + 4); // Thursday, never confirms
        dialog(u4, pastMonday.plusDays(4), s + 5); // Friday, later deleted
        // duplicate emission of u1's Monday dialog (same natural key) -> deduplicated
        assertNull(metricEventService.record(MetricCode.PRIVATE_DIALOG_COMPLETED, u1.id,
                atShanghai(pastMonday, 20, 0, 0), "DIALOG_SESSION", String.valueOf(s + 1),
                null, null, Map.of("sessionId", String.valueOf(s + 1))));
        // non-response path: u5/u6 never confirm anything (K1 non-respondents stay denominator)
        dialog(u5, pastMonday, s + 6);
        dialog(u6, pastMonday.plusDays(1), s + 7);

        // --- Test-account isolation and consent refusal: no rows at all.
        assertNull(metricEventService.record(MetricCode.PRIVATE_DIALOG_COMPLETED, sandbox.id,
                atShanghai(pastMonday, 20, 0, 0), "DIALOG_SESSION", String.valueOf(s + 8),
                null, null, Map.of("sessionId", String.valueOf(s + 8))));
        assertNull(metricEventService.record(MetricCode.PRIVATE_DIALOG_COMPLETED, declined.id,
                atShanghai(pastMonday, 20, 0, 0), "DIALOG_SESSION", String.valueOf(s + 9),
                null, null, Map.of("sessionId", String.valueOf(s + 9))));
        assertNull(metricEventService.record(MetricCode.PRIVATE_DIALOG_COMPLETED, 999_999_999L,
                atShanghai(pastMonday, 20, 0, 0), "DIALOG_SESSION", "ghost",
                null, null, Map.of("sessionId", "ghost")));

        // --- K1 connected: thread 800 qualified (u1+u2 both send), confirmed by both.
        letterSend(u1, pastMonday, 800);
        letterSend(u2, pastMonday.plusDays(1), 800);
        confirmConnection(u1, pastMonday.plusDays(3), 800);
        confirmConnection(u2, pastMonday.plusDays(4), 800);
        letterSend(u3, pastMonday.plusDays(2), 801); // single sender -> not qualified

        // --- K2: u5 returns in the 28..34d window; everyone else in cohort does not.
        dialog(u5, pastMonday.plusDays(30), s + 10);
        // u6 activated Tuesday and never returns; u1..u4 activated this week as well.

        // --- G-SAFE: one HIGH crisis incident for u3.
        metricEventService.record(MetricCode.SAFETY_INCIDENT, u3.id,
                atShanghai(pastMonday.plusDays(3), 15, 0, 0), "DIALOG_SESSION", String.valueOf(s + 4),
                "HIGH", "CRISIS_KEYWORD",
                Map.of("safetyEventId", "se-1", "triggerScene", "AURORA"));

        // --- G-TRUST: two completed rights actions.
        metricEventService.record(MetricCode.RIGHTS_ACTION_COMPLETED, u2.id,
                atShanghai(pastMonday.plusDays(2), 10, 0, 0), "MEMORY", "42",
                "MEMORY_FORGET", "EMBEDDING",
                Map.of("receiptId", "r-1", "subjectType", "MEMORY", "affectedCount", "5"));
        metricEventService.record(MetricCode.RIGHTS_ACTION_COMPLETED, u4.id,
                atShanghai(pastMonday.plusDays(4), 11, 0, 0), "CAPSULE", "7",
                "CAPSULE_REVOKE", "MATCHING_VECTOR",
                Map.of("receiptId", "r-2", "subjectType", "CAPSULE", "affectedCount", "3"));

        // --- Refund/late ingestion: captured Monday, refunded Friday, both anchored to pastWeek.
        metricEventService.record(MetricCode.PAYMENT_CAPTURED, u1.id,
                atShanghai(pastMonday, 9, 0, 0), "ORDER", "o-1",
                null, null, Map.of("orderId", "o-1", "channel", "WXPAY",
                        "amountCents", "1999", "currency", "CNY"));
        metricEventService.record(MetricCode.REFUND_SETTLED, u1.id,
                atShanghai(pastMonday.plusDays(4), 9, 0, 0), "ORDER", "o-1",
                null, null, Map.of("orderId", "o-1", "channel", "WXPAY",
                        "amountCents", "1999", "currency", "CNY"));

        // ================= assertions (hand-derived deltas over the baseline) =================
        K1WeeklyReport k1 = queryService.k1Weekly(pastWeek);
        // K1 denominator: u1,u2,u3,u4,u5,u6 each had >=1 completed dialog in pastWeek.
        assertEquals(k1Before.privateDenominator() + 6, k1.privateDenominator());
        assertTrue(k1.privateMatured());
        assertEquals(k1Before.privateNumerator() + 1, k1.privateNumerator(),
                "only u1 confirmed AND used core on two distinct days of the same week");
        assertEquals(k1Before.connectedQualifiedExchanges() + 1, k1.connectedQualifiedExchanges());
        assertEquals(k1Before.connectedConfirmedExchanges() + 1, k1.connectedConfirmedExchanges());
        assertTrue(k1.connectedMatured());

        GSafeWeeklyReport gSafe = queryService.gSafeWeekly(pastWeek);
        // core sessions: u1x2 + u2 + u3 + u4 + u5 + u6 = 7 completed dialogs
        assertEquals(gSafeBefore.coreSessions() + 7, gSafe.coreSessions());
        assertEquals(gSafeBefore.incidentsTotal() + 1, gSafe.incidentsTotal());
        assertEquals(gSafeBefore.incidentsBySeverity().getOrDefault("HIGH", 0L) + 1,
                gSafe.incidentsBySeverity().get("HIGH"));

        GTrustWeeklyReport gTrust = queryService.gTrustWeekly(pastWeek);
        assertEquals(gTrustBefore.actionsByType().getOrDefault("MEMORY_FORGET", 0L) + 1,
                gTrust.actionsByType().get("MEMORY_FORGET"));
        assertEquals(gTrustBefore.actionsByType().getOrDefault("CAPSULE_REVOKE", 0L) + 1,
                gTrust.actionsByType().get("CAPSULE_REVOKE"));
        assertEquals(gTrustBefore.affectedRecordsTotal() + 8, gTrust.affectedRecordsTotal());

        // ---------------- account deletion: denominators must not shrink ----------------
        metricEventService.anonymizeUser(u4.id);
        K1WeeklyReport k1After = queryService.k1Weekly(pastWeek);
        assertEquals(k1.privateDenominator(), k1After.privateDenominator(),
                "deleted user keeps the historical K1 denominator via rollup");
        assertEquals(k1.privateNumerator(), k1After.privateNumerator(),
                "deleted user can never be a numerator");
        GTrustWeeklyReport gTrustAfter = queryService.gTrustWeekly(pastWeek);
        assertEquals(gTrust.actionsByType().get("MEMORY_FORGET"),
                gTrustAfter.actionsByType().get("MEMORY_FORGET"));
        assertEquals(gTrustBefore.actionsByType().getOrDefault("ANONYMIZED", 0L) + 1,
                gTrustAfter.actionsByType().get("ANONYMIZED"),
                "deleted user's rights action survives only as an anonymous count");
        assertEquals(gTrust.affectedRecordsTotal(), gTrustAfter.affectedRecordsTotal(),
                "affected totals survive deletion");
        GSafeWeeklyReport gSafeAfter = queryService.gSafeWeekly(pastWeek);
        assertEquals(gSafe.coreSessions(), gSafeAfter.coreSessions(),
                "core-session denominator stable via rollup");
        assertEquals(0, eventMapper.selectList(null).stream()
                .filter(e -> u4.id.equals(e.userId)).count(),
                "deleted user's rows leave the event store entirely");

        // ---------------- K2 cohort: original size preserved through deletion ----------------
        List<K2CohortReport> cohorts = queryService.k2Cohorts(true);
        K2CohortReport pastCohort = cohorts.stream()
                .filter(c -> pastWeek.equals(c.activationWeek())).findFirst().orElseThrow();
        assertTrue(pastCohort.matured());
        assertEquals(cohortBefore.observedUsers() + 6, pastCohort.observedUsers(),
                "u1,u2,u3,u4(deleted),u5,u6 — deletion keeps the original cohort size");
        assertEquals(cohortBefore.retainedUsers() + 1, pastCohort.retainedUsers(),
                "only u5 had a core-value day in t0+28..34");
    }

    private static final class TemporalAdjustersPrevOrSameMonday
            implements java.time.temporal.TemporalAdjuster {
        @Override
        public java.time.temporal.Temporal adjustInto(java.time.temporal.Temporal temporal) {
            LocalDate date = LocalDate.from(temporal);
            while (date.getDayOfWeek() != DayOfWeek.MONDAY) {
                date = date.minusDays(1);
            }
            return date;
        }
    }

    @Test
    void crossWeekUtcShanghaiBoundary() {
        // 2026-09-06T16:05:00Z is 2026-09-07 00:05 Monday in Asia/Shanghai -> ISO week 2026-W37.
        User u = human("cp03-tz-" + System.nanoTime());
        CommercialMetricEvent event = metricEventService.record(
                MetricCode.PRIVATE_DIALOG_COMPLETED, u.id,
                java.time.Instant.parse("2026-09-06T16:05:00Z"),
                "DIALOG_SESSION", "tz-1", null, null, Map.of("sessionId", "tz-1"));
        assertNotNull(event);
        assertEquals("2026-09-07", event.anchorDay);
        assertEquals("2026-W37", event.anchorWeek);
        assertEquals(java.time.LocalDateTime.parse("2026-09-06T16:05"),
                event.occurredAtUtc.truncatedTo(java.time.temporal.ChronoUnit.MINUTES));
    }

    @Test
    void p0PropsAreRejectedByAllowlist() {
        User u = human("cp03-p0-" + System.nanoTime());
        Map<String, Object> leaking = new HashMap<>();
        leaking.put("letterBody", "亲爱的...");
        assertThrows(IllegalArgumentException.class, () -> metricEventService.record(
                MetricCode.CONNECTED_REAL_SEND, u.id, clock.instant(),
                "LETTER_THREAD", "p0-1", null, null, leaking));
        Map<String, Object> tooLong = new HashMap<>();
        tooLong.put("threadId", "x".repeat(500));
        CommercialMetricEvent capped = metricEventService.record(
                MetricCode.CONNECTED_REAL_SEND, u.id, clock.instant(),
                "LETTER_THREAD", "p0-2", null, null, tooLong);
        assertNotNull(capped);
        assertTrue(capped.props.length() <= 260, "prop values are length-capped");
        assertTrue(capped.props.contains("\"threadId\":\"" + "x".repeat(200) + "\""),
                "the stored value is exactly the 200-char cap");
    }
}
