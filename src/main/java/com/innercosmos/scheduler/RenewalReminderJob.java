package com.innercosmos.scheduler;

import com.innercosmos.payments.entitlement.RenewalReminderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * CP-46 续费前显著提醒 poller: runs daily on scheduler/all runtime roles. The service
 * itself is idempotent per charging period, so a missed or repeated poll is harmless —
 * and the api role never emits billing reminders.
 */
@Component
@ConditionalOnExpression("'${inner-cosmos.runtime.role:all}' == 'all' or '${inner-cosmos.runtime.role:all}' == 'scheduler'")
public class RenewalReminderJob {

    private static final Logger log = LoggerFactory.getLogger(RenewalReminderJob.class);

    private final RenewalReminderService reminders;

    public RenewalReminderJob(RenewalReminderService reminders) {
        this.reminders = reminders;
    }

    @Scheduled(cron = "${inner-cosmos.payments.renewal-reminder.cron:0 0 9 * * *}")
    public void poll() {
        try {
            reminders.remindExpiringRenewals();
        } catch (RuntimeException e) {
            log.warn("Renewal reminder poll failed: {}", e.getMessage());
        }
    }
}
