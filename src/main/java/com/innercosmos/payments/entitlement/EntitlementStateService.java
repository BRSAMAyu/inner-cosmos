package com.innercosmos.payments.entitlement;

import com.innercosmos.entity.Entitlement;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CP-46 unified entitlement state machine. Inputs are server-side facts only — payment
 * events from the CP-47 ledger pipeline (客户端只能展示不能宣告支付成功) and channel
 * subscription notifications. Every input is deduplicated by its channel notification id:
 * a duplicate acks, it never double-grants or double-extends (支付重复通知/通知去重).
 */
public interface EntitlementStateService {

    /** Channel subscription notification kinds normalized into the unified machine. */
    enum ChannelSubscriptionState {
        PURCHASE_PENDING, TRIAL_STARTED, RENEWED, GRACE_ENTERED, CANCELLED, EXPIRED, RESTORED
    }

    /** One entitled product as shown to the user (跨端权益的统一视图). */
    record EntitlementView(
            String productId,
            String state,
            LocalDateTime periodStart,
            LocalDateTime periodEnd,
            boolean autoRenew,
            boolean cancelAtPeriodEnd,
            /** 权益窗口重置时间：订阅型产品的配额窗口随周期重置。 */
            LocalDateTime quotaResetsAt) {
    }

    /**
     * A verified PAYMENT_SUCCEEDED fact: activates a pending/new entitlement or renews an
     * existing one by one period (month-end clamped, Asia/Shanghai calendar). Idempotent
     * per providerEventId. A payment onto a REVOKED row is a re-purchase and revives it.
     */
    Entitlement onPaymentSucceeded(long userId, String productId, String channel,
                                   String orderId, String providerEventId,
                                   LocalDateTime occurredAt);

    /**
     * A verified REFUND_SUCCEEDED fact: 撤销 the entitlement (REVOKED), whatever its
     * current state. Idempotent per providerEventId. A later restore cannot revive it —
     * only a new payment (re-purchase) can.
     */
    Entitlement onRefundSucceeded(long userId, String productId, String providerEventId,
                                  LocalDateTime occurredAt);

    /**
     * A channel subscription notification: normalizes the channel-original state onto the
     * unified machine. CANCELLED keeps the row entitled until period end (取消保留到期末,
     * the 取消入口与续费提醒清晰 requirement); unknown future states are rejected without
     * any state change or audit row — fail-closed, like the payment status mapping.
     */
    Entitlement onChannelSubscriptionState(long userId, String productId, String channel,
                                           String notificationId,
                                           ChannelSubscriptionState channelState,
                                           LocalDateTime occurredAt);

    /** User-initiated cancellation: auto-renew off, entitled until period end. */
    Entitlement cancelByUser(long userId, String productId);

    /** The user's unified entitlement view, lazily expiring stale periods. */
    List<EntitlementView> snapshot(long userId);
}
