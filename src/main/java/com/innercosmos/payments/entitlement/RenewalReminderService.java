package com.innercosmos.payments.entitlement;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.Entitlement;
import com.innercosmos.mapper.EntitlementMapper;
import com.innercosmos.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * CP-46 续费前显著提醒 (the consumer-protection rule: 自动续费前显著提示). For every
 * auto-renewing entitlement whose period ends inside the reminder window, the user gets
 * ONE durable notification per period — idempotent via the notification's
 * idempotency_key, which embeds the period end so a rescan (or a pod restart mid-window)
 * never double-reminds. The reminder names the price moment (续费日期), points at the
 * always-available cancel entry (取消入口永不缺席), and is delivered through the same
 * notification pipeline that fans out to the push outbox — 显著, not a log line.
 */
@Service
public class RenewalReminderService {

    private static final Logger log = LoggerFactory.getLogger(RenewalReminderService.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final EntitlementMapper entitlementMapper;
    private final NotificationService notifications;
    private final boolean enabled;
    private final int reminderDays;

    public RenewalReminderService(
            EntitlementMapper entitlementMapper,
            NotificationService notifications,
            @Value("${inner-cosmos.payments.renewal-reminder.enabled:true}") boolean enabled,
            @Value("${inner-cosmos.payments.renewal-reminder.days:3}") int reminderDays) {
        this.entitlementMapper = entitlementMapper;
        this.notifications = notifications;
        this.enabled = enabled;
        this.reminderDays = Math.max(1, reminderDays);
    }

    /** One scan pass; returns how many reminders were created (for the scheduler log). */
    public int remindExpiringRenewals() {
        if (!enabled) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<Entitlement> expiring = entitlementMapper.selectList(
                new QueryWrapper<Entitlement>()
                        .in("state", List.of(Entitlement.ACTIVE, Entitlement.TRIAL,
                                Entitlement.GRACE_PERIOD))
                        .eq("auto_renew", true)
                        .isNotNull("period_end")
                        .gt("period_end", now)
                        .le("period_end", now.plusDays(reminderDays)));
        int created = 0;
        for (Entitlement entitlement : expiring) {
            // Key carries the period end: one reminder per charging period, forever.
            String periodKey = "RENEWAL:" + DAY.format(entitlement.periodEnd) + ":"
                    + entitlement.id;
            notifications.notifyOnce(entitlement.userId, "RENEWAL_REMINDER",
                    "订阅续费提醒",
                    String.format(Locale.ROOT,
                            "你的订阅将于 %s（UTC）到期并按原价自动续费。如不需要，可在「我的权益」中随时取消，"
                                    + "取消后当前周期结束前仍可使用。",
                            entitlement.periodEnd.format(DAY)),
                    entitlement.id, periodKey);
            created++;
        }
        if (created > 0) {
            log.info("Renewal reminders issued for {} expiring auto-renew entitlements", created);
        }
        return created;
    }
}
