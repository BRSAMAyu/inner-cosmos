package com.innercosmos.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.ai.proactive.ProactiveDeliveryChannel;
import com.innercosmos.ai.proactive.QuietWindowResolver;
import com.innercosmos.entity.WakeIntent;
import com.innercosmos.mapper.NotificationMapper;
import com.innercosmos.mapper.UserProfileMapper;
import com.innercosmos.mapper.WakeIntentMapper;
import com.innercosmos.safety.SafetyBoundaryFilter;
import com.innercosmos.scheduler.WakeIntentDeliveryJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CP-26 closing-checklist §2-5 (J07 acceptance, negative paths). Covers, against the real
 * persistence layer:
 * <ul>
 *   <li>quiet hours: a due intent inside the do-not-disturb window is withheld with a visible
 *       DEFERRED + deferred_until row state (never silently dropped) and delivered once the
 *       window has ended;</li>
 *   <li>reschedule: the old slot stops delivering, the new one delivers, repeated rescheduling
 *       to the same target is idempotent;</li>
 *   <li>claim/delivery idempotency: concurrent duplicate claims and duplicate deliveries produce
 *       exactly one delivered notification (PLANNED→CLAIMED→DEFERRED/FIRED state machine with
 *       conditional updates);</li>
 *   <li>regression: outside any quiet window delivery is unchanged.</li>
 * </ul>
 */
@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class WakeIntentQuietWindowAndIdempotencyTest {
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    @Autowired WakeIntentService service;
    @Autowired WakeIntentMapper mapper;
    @Autowired NotificationMapper notificationMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired QuietWindowResolver resolver;
    @Autowired ProactiveDeliveryChannel liveChannel;
    @Autowired SafetyBoundaryFilter safety;
    @Autowired UserProfileMapper profiles;
    @Autowired WakeIntentRelevanceEvaluator relevance;

    @AfterEach
    void clean() {
        mapper.delete(new QueryWrapper<>());
        notificationMapper.delete(new QueryWrapper<>());
    }

    private WakeIntentDeliveryJob jobFor(WakeIntentQuietHoursPolicy quietHours) {
        return new WakeIntentDeliveryJob(service, resolver, quietHours, liveChannel, safety, profiles, relevance);
    }

    /** Legacy behaviour: the shipped default has quiet hours disabled. */
    private WakeIntentQuietHoursPolicy quietHoursDisabled() {
        return new WakeIntentQuietHoursPolicy(false, "22:00", "07:00", profiles);
    }

    /** A platform window that provably contains "now" (UTC) and ends roughly two hours later. */
    private WakeIntentQuietHoursPolicy quietWindowAroundNow() {
        LocalTime now = LocalTime.now(ZoneOffset.UTC);
        return new WakeIntentQuietHoursPolicy(true,
            now.minusHours(1).format(HHMM), now.plusHours(2).format(HHMM), profiles);
    }

    /**
     * Scoped by the fixture user: the full suite runs with cached application contexts
     * whose schedulers may write unrelated notifications concurrently -- a global count
     * would flake under exactly that interference.
     */
    private long notifications(long userId) {
        return notificationMapper.selectCount(
                new QueryWrapper<com.innercosmos.entity.Notification>().eq("user_id", userId));
    }

    @Test
    void quietWindowDefersVisiblyThenDeliversExactlyOnceAfterTheWindowEnds() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        WakeIntent intent = service.schedule(960001L, "深夜按约回来", "约定时间到了，但现在是静默时段",
            "我来赴约了。", now.minusMinutes(2), now.minusMinutes(1), now.plusHours(8), "UTC", null);

        jobFor(quietWindowAroundNow()).run();

        // Negative: nothing delivered inside the quiet window, and the row explains why.
        WakeIntent withheld = mapper.selectById(intent.id);
        assertThat(withheld.status).isEqualTo("DEFERRED");
        assertThat(withheld.deferredUntil)
            .as("defer target must be the quiet window end (~2h ahead), not a blind +15min re-poll")
            .isAfter(now.plusMinutes(110))
            .isBefore(now.plusMinutes(130));
        assertThat(withheld.outcome).isEqualTo("DELAY");
        assertThat(withheld.outcomeReason).isEqualTo("boundary:platform_quiet_hours");
        assertThat(withheld.firedAt).isNull();
        // The user's agreed time is not rewritten by the defer (DB timestamps truncate to micros).
        assertThat(withheld.preferredAt.truncatedTo(java.time.temporal.ChronoUnit.SECONDS))
            .isEqualTo(intent.preferredAt.truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        assertThat(notifications(960001L)).isZero();
        // A concurrent claim scan before the window ends must not pick the deferred row up.
        assertThat(service.claimDue("worker", 10, Duration.ofMinutes(1))).isEmpty();

        // Window over (equivalent persisted state: deferred_until already elapsed) → deliver.
        jdbc.update("UPDATE tb_wake_intent SET deferred_until=? WHERE id=?", now.minusMinutes(1), intent.id);
        jobFor(quietHoursDisabled()).run();

        WakeIntent delivered = mapper.selectById(intent.id);
        assertThat(delivered.status).isEqualTo("FIRED");
        assertThat(delivered.firedAt).isNotNull();
        assertThat(notifications(960001L)).isEqualTo(1);
    }

    @Test
    void rescheduledIntentSkipsItsOldSlotFiresAtTheNewOneAndRepeatRescheduleIsIdempotent() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        WakeIntent intent = service.schedule(960002L, "改期后的约定", "先按约定时间回来", "我按新时间来。",
            now.minusMinutes(2), now.minusMinutes(1), now.plusHours(8), "UTC", null);

        LocalDateTime tomorrow = now.plusDays(1);
        WakeIntent moved = service.reschedule(960002L, intent.id,
            tomorrow.minusMinutes(10), tomorrow, tomorrow.plusHours(2));

        // Negative: the old slot is due right now but must not deliver.
        jobFor(quietHoursDisabled()).run();
        assertThat(mapper.selectById(intent.id).status).isEqualTo("PLANNED");
        assertThat(notifications(960002L)).isZero();
        assertThat(service.claimDue("worker", 10, Duration.ofMinutes(1))).isEmpty();

        // Repeated rescheduling to the same target stays idempotent: one row, same window.
        WakeIntent movedAgain = service.reschedule(960002L, intent.id,
            tomorrow.minusMinutes(10), tomorrow, tomorrow.plusHours(2));
        assertThat(movedAgain.id).isEqualTo(intent.id);
        assertThat(movedAgain.preferredAt).isEqualTo(moved.preferredAt);
        assertThat(service.listActive(960002L)).hasSize(1);

        // Moving the agreement back to "now": the new slot delivers exactly once.
        service.reschedule(960002L, intent.id, now.minusMinutes(2), now.minusMinutes(1), now.plusHours(8));
        jobFor(quietHoursDisabled()).run();
        WakeIntent fired = mapper.selectById(intent.id);
        assertThat(fired.status).isEqualTo("FIRED");
        assertThat(notifications(960002L)).isEqualTo(1);
    }

    @Test
    void concurrentDuplicateClaimsAndDeliveriesProduceExactlyOneNotification() throws Exception {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        service.schedule(960003L, "并发领取", "只投递一次", "只此一条。",
            now.minusMinutes(2), now.minusMinutes(1), now.plusHours(8), "UTC", null);

        CountDownLatch claimStart = new CountDownLatch(1);
        List<WakeIntent> first;
        List<WakeIntent> second;
        try (var pool = Executors.newFixedThreadPool(2)) {
            var claimA = pool.submit(() -> { claimStart.await(); return service.claimDue("worker-a", 10, Duration.ofMinutes(1)); });
            var claimB = pool.submit(() -> { claimStart.await(); return service.claimDue("worker-b", 10, Duration.ofMinutes(1)); });
            claimStart.countDown();
            first = claimA.get();
            second = claimB.get();
        }
        // Negative: two workers racing for the same due intent — exactly one lease is granted.
        assertThat(first.size() + second.size()).isEqualTo(1);
        WakeIntent claimed = first.isEmpty() ? second.getFirst() : first.getFirst();

        // Both workers then try to deliver with the same claimed snapshot.
        CountDownLatch deliverStart = new CountDownLatch(1);
        boolean deliveredByA;
        boolean deliveredByB;
        try (var pool = Executors.newFixedThreadPool(2)) {
            var deliveryA = pool.submit(() -> { deliverStart.await();
                return service.finishWithNotification(claimed, "CONVERT_TO_IN_APP", "offline",
                    "只投递一次", "只此一条。"); });
            var deliveryB = pool.submit(() -> { deliverStart.await();
                return service.finishWithNotification(claimed, "CONVERT_TO_IN_APP", "offline",
                    "只投递一次", "只此一条。"); });
            deliverStart.countDown();
            deliveredByA = deliveryA.get();
            deliveredByB = deliveryB.get();
        }
        // Negative: the second delivery must be rejected — no second notification/push ever.
        assertThat(deliveredByA ^ deliveredByB)
            .as("exactly one of the duplicate deliveries may succeed (a=%s b=%s)", deliveredByA, deliveredByB)
            .isTrue();
        assertThat(notifications(960003L)).isEqualTo(1);
        WakeIntent row = mapper.selectById(claimed.id);
        assertThat(row.status).isEqualTo("FIRED");
        assertThat(row.firedAt).isNotNull();
        assertThat(row.claimToken).isNull();
    }

    @Test
    void dueIntentOutsideAnyQuietWindowDeliversWithoutDefer() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        WakeIntent intent = service.schedule(960004L, "白天按约回来", "白天约定", "我来赴约了。",
            now.minusMinutes(2), now.minusMinutes(1), now.plusHours(8), "UTC", null);

        jobFor(quietHoursDisabled()).run();

        WakeIntent row = mapper.selectById(intent.id);
        assertThat(row.status).isEqualTo("FIRED");
        assertThat(row.deferredUntil).isNull();
        assertThat(row.outcomeReason).isEqualTo("user_offline");
        assertThat(notifications(960004L)).isEqualTo(1);
    }

    @Test
    void deferredIntentOutlivingItsLatestWindowExpiresVisiblyInsteadOfVanishing() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        WakeIntent intent = service.schedule(960005L, "静默窗内过期", "过期可见", "不该被投递的约定。",
            now.minusMinutes(2), now.minusMinutes(1), now.plusMinutes(30), "UTC", null);
        WakeIntent claimed = service.claimDue("worker", 5, Duration.ofMinutes(1)).getFirst();

        assertThat(service.defer(claimed, now.plusHours(6), "boundary:platform_quiet_hours")).isTrue();
        WakeIntent deferred = mapper.selectById(intent.id);
        assertThat(deferred.status).isEqualTo("DEFERRED");
        assertThat(deferred.deferredUntil.truncatedTo(java.time.temporal.ChronoUnit.SECONDS))
            .isEqualTo(now.plusHours(6).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        assertThat(service.claimDue("worker", 5, Duration.ofMinutes(1))).isEmpty();

        // latest_at elapses while still deferred (equivalent persisted state): visible DROP,
        // never a silent disappearance and never a delivery.
        jdbc.update("UPDATE tb_wake_intent SET latest_at=? WHERE id=?", now.minusMinutes(1), intent.id);
        assertThat(service.expirePastDue()).isEqualTo(1);
        WakeIntent expired = mapper.selectById(intent.id);
        assertThat(expired.status).isEqualTo("EXPIRED");
        assertThat(expired.outcome).isEqualTo("DROP");
        assertThat(expired.outcomeReason).isEqualTo("latest_at_elapsed");
        assertThat(service.claimDue("worker", 5, Duration.ofMinutes(1))).isEmpty();
        assertThat(notifications(960005L)).isZero();
    }
}
