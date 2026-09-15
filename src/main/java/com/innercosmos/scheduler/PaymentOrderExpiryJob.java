package com.innercosmos.scheduler;

import com.innercosmos.payments.PaymentOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * CP-45 §2-21 order-expiry sweep: flips overdue CREATED orders to the EXPIRED terminal in
 * batches, so an abandoned checkout stops being payable even if nobody ever touches it
 * again. The flip is a conditional UPDATE on status='CREATED', which makes the sweep
 * idempotent against the lazy per-callback expiry and against itself; a missed or repeated
 * run is harmless (no double flip is representable).
 */
@Component
@ConditionalOnExpression("'${inner-cosmos.runtime.role:all}' == 'all' or '${inner-cosmos.runtime.role:all}' == 'scheduler'")
public class PaymentOrderExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentOrderExpiryJob.class);

    private final PaymentOrderService orders;

    public PaymentOrderExpiryJob(PaymentOrderService orders) {
        this.orders = orders;
    }

    @Scheduled(cron = "${inner-cosmos.payments.order-expiry.cron:0 */5 * * * *}")
    public void sweep() {
        try {
            int expired = orders.expireOverdue();
            if (expired > 0) {
                log.info("Order expiry sweep flipped {} overdue order(s) to EXPIRED", expired);
            }
        } catch (RuntimeException e) {
            log.warn("Order expiry sweep failed: {}", e.getMessage());
        }
    }
}
