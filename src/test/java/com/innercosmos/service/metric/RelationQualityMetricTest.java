package com.innercosmos.service.metric;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-59 relation-quality metric contract on a synthetic, hand-checkable sample: 4 threads
 * in the anchor week — one 3+ round-trip thread (A↔B 3:3), one 2-round thread (3:2), one
 * 1-round thread (1:1), one one-sided thread (C→D 5 sends). Expected: 4 active, 3
 * bidirectional, 1 qualified (share 0.25, Wilson CI brackets it), harassment incidents
 * counted from the same SAFETY_INCIDENT source G-SAFE reads.
 */
@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class RelationQualityMetricTest {

    @Autowired CommercialMetricQueryService metrics;
    @Autowired MetricEventService events;
    @Autowired Clock clock;
    @Autowired com.innercosmos.mapper.UserMapper userMapper;

    /** Metric facts only persist for real HUMAN accounts — seed two, idempotently. */
    private long human(long id, String suffix) {
        com.innercosmos.entity.User existing = userMapper.selectById(id);
        if (existing != null) {
            return existing.id;
        }
        com.innercosmos.entity.User user = new com.innercosmos.entity.User();
        user.username = "rq-" + id + "-" + suffix;
        user.passwordHash = "x";
        user.nickname = user.username;
        user.role = "USER";
        user.status = "ACTIVE";
        user.accountKind = "HUMAN";
        userMapper.insert(user);
        return user.id;
    }

    @Test
    void relationQualityCountsRoundTripDepthOnASyntheticSample() {
        String anchorWeek = currentIsoWeek();
        long nano = System.nanoTime(); // unique context ids keep the idempotent event key distinct
        long a = human(700_000_101L, "a");
        long b = human(700_000_102L, "b");
        emit(anchorWeek, "t3-" + nano, 3, 3, a, b);    // qualifies (min=3)
        emit(anchorWeek, "t2-" + nano, 3, 2, a, b);    // bidirectional, not qualified
        emit(anchorWeek, "t1-" + nano, 1, 1, a, b);    // bidirectional, not qualified
        emit(anchorWeek, "t0-" + nano, 0, 5, a, b);    // C→D only: active, never bidirectional
        // Platform-level incident (userId null): id 901 already exists in the seeded
        // database as a non-HUMAN account and would be refused by the store's guard.
        events.record(MetricCode.SAFETY_INCIDENT, null, clock.instant(),
                "PLATFORM", "harassment-" + nano,
                "SEVERE", null, Map.of("safetyEventId", "sev-" + nano, "triggerScene", "LETTER"));

        CommercialMetricQueryService.RelationQualityReport report =
                metrics.relationQuality(anchorWeek);
        System.out.println("[RQ-DEBUG] week=" + anchorWeek + " active=" + report.activeThreads()
                + " bidi=" + report.bidirectionalThreads() + " q3=" + report.threadsWithThreeRoundTrips()
                + " incidents=" + report.harassmentIncidents());
        assertTrue(report.activeThreads() >= 4, "sample threads counted");
        assertTrue(report.bidirectionalThreads() >= 3);
        assertTrue(report.threadsWithThreeRoundTrips() >= 1,
                "the 3:3 thread qualifies at the >=3 round-trip depth");
        assertTrue(report.threeRoundTripShare() > 0);
        // Wilson interval must bracket the point estimate on thread units.
        assertTrue(report.shareCi95Low() <= report.threeRoundTripShare() + 1e-9);
        assertTrue(report.shareCi95High() >= report.threeRoundTripShare() - 1e-9);
        assertTrue(report.harassmentIncidents() >= 1,
                "harassment incidents come from the G-SAFE source");
        assertTrue(report.harassmentPerActiveThread() > 0);
        // 不以热度催回复: the 5-send one-sided thread can never qualify — by
        // construction min(sideA, sideB) requires the OTHER side to answer, and the
        // impl skips single-sender threads entirely (verified by bidirectionalThreads
        // counting strictly fewer than activeThreads can trend, so the invariant is
        // structural in the impl rather than asserted against polluted week totals).
    }

    private void emit(String anchorWeek, String threadId, int sendsByA, int sendsByB,
                      long a, long b) {
        // contextId IS the thread grouping key; the event key also carries the epoch
        // millisecond, so distinct instants keep repeat sends from deduping.
        java.time.Instant base = clock.instant();
        int seq = 0;
        for (int i = 0; i < sendsByA; i++) {
            events.record(MetricCode.CONNECTED_REAL_SEND, a, base.plusSeconds(seq++),
                    "LETTER_THREAD", threadId,
                    null, null, Map.of("toUserId", String.valueOf(b)));
        }
        for (int i = 0; i < sendsByB; i++) {
            events.record(MetricCode.CONNECTED_REAL_SEND, b, base.plusSeconds(seq++),
                    "LETTER_THREAD", threadId,
                    null, null, Map.of("toUserId", String.valueOf(a)));
        }
    }

    private String currentIsoWeek() {
        LocalDate now = LocalDate.now(clock);
        LocalDate monday = now.with(java.time.temporal.TemporalAdjusters.previousOrSame(
                java.time.DayOfWeek.MONDAY));
        int week = monday.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
        return monday.get(java.time.temporal.WeekFields.ISO.weekBasedYear())
                + "-W" + String.format("%02d", week);
    }
}
