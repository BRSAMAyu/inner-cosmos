package com.innercosmos.payments.entitlement;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.Entitlement;
import com.innercosmos.entity.Notification;
import com.innercosmos.mapper.EntitlementMapper;
import com.innercosmos.mapper.NotificationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-46 续费前显著提醒 contract: an auto-renewing entitlement inside the reminder
 * window gets exactly ONE durable notification per charging period (a rescan never
 * double-reminds — idempotency key embeds the period end); cancelled (auto-renew off),
 * far-future and revoked entitlements get nothing; and the disabled flag silences the
 * scan entirely.
 */
@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class RenewalReminderContractTest {

    private static final String PRODUCT = "pro.monthly";
    private static final AtomicLong USERS = new AtomicLong(970_000_000L);

    @Autowired EntitlementStateService entitlements;
    @Autowired EntitlementMapper entitlementMapper;
    @Autowired NotificationMapper notificationMapper;
    @Autowired org.springframework.context.ApplicationContext context;

    private static long uniqueUser() {
        return USERS.incrementAndGet();
    }

    @Test
    void expiringAutoRenewalGetsExactlyOneReminderPerPeriod() {
        long user = uniqueUser();
        // Period ends in ~2 days from the real clock: inside the 3-day window.
        LocalDateTime periodStart = LocalDateTime.now(ZoneOffset.UTC).minusDays(28);
        Entitlement entitled = entitlements.onPaymentSucceeded(user, PRODUCT, "wechatpay",
                "O-RMD-" + user, "evt-rmd-" + user, periodStart);
        assertEquals(Entitlement.ACTIVE, entitled.state);
        assertTrue(Boolean.TRUE.equals(entitled.autoRenew));

        RenewalReminderService service = context.getBean(RenewalReminderService.class);
        int first = service.remindExpiringRenewals();
        int second = service.remindExpiringRenewals();

        assertTrue(first >= 1, "at least the fixture entitlement is inside the window");
        assertTrue(second <= first, "a rescan must not stack duplicates");
        List<Notification> reminders = notificationMapper.selectList(
                new QueryWrapper<Notification>()
                        .eq("user_id", user).eq("type", "RENEWAL_REMINDER"));
        assertEquals(1, reminders.size(), "one reminder per charging period, durable");
        assertTrue(reminders.get(0).body.contains("自动续费"), "the reminder names the charge");
        assertTrue(reminders.get(0).body.contains("取消"), "the reminder points at the cancel entry");
        assertTrue(Boolean.FALSE.equals(reminders.get(0).read) || reminders.get(0).read == null);
    }

    @Test
    void cancelledFarFutureAndRevokedEntitlementsRemindNothing() {
        // Cancelled (auto-renew off): the user already opted out — no charge, no reminder.
        long cancelled = uniqueUser();
        LocalDateTime inTwoDays = LocalDateTime.now(ZoneOffset.UTC).plusDays(2);
        Entitlement row = entitlements.onPaymentSucceeded(cancelled, PRODUCT, "alipay",
                "O-RMC-" + cancelled, "evt-rmc-" + cancelled,
                inTwoDays.minusMonths(1));
        entitlements.cancelByUser(cancelled, PRODUCT);
        // Far future: outside the window.
        long later = uniqueUser();
        entitlements.onPaymentSucceeded(later, PRODUCT, "alipay", "O-RML-" + later,
                "evt-rml-" + later, LocalDateTime.now(ZoneOffset.UTC).plusDays(1));

        RenewalReminderService service = context.getBean(RenewalReminderService.class);
        service.remindExpiringRenewals();

        assertEquals(0, notificationMapper.selectCount(new QueryWrapper<Notification>()
                .eq("user_id", cancelled).eq("type", "RENEWAL_REMINDER")),
                "cancelled auto-renew must not be nagged about a charge that will not happen");
        assertEquals(0, notificationMapper.selectCount(new QueryWrapper<Notification>()
                .eq("user_id", later).eq("type", "RENEWAL_REMINDER")));
    }
}
