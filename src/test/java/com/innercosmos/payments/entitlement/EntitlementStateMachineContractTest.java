package com.innercosmos.payments.entitlement;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.Entitlement;
import com.innercosmos.entity.EntitlementEvent;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.EntitlementMapper;
import com.innercosmos.payments.entitlement.EntitlementStateService.ChannelSubscriptionState;
import com.innercosmos.payments.entitlement.EntitlementStateService.EntitlementView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CP-46 unified entitlement state machine contract: full channel lifecycle
 * (purchase-pending → trial → renewed → grace → cancelled → expired), payment-driven
 * activation and stacked renewal with Asia/Shanghai month-end clamping, duplicate
 * notification dedup (never double-grant), sticky refund revocation that restore cannot
 * launder but a new purchase can, cancellation keeping paid time, lazy expiry, and the
 * structural promise that safety/correction/export/delete capabilities are never
 * pay-gated (安全/纠正/导出删除永不付费解锁).
 */
@SpringBootTest
class EntitlementStateMachineContractTest {

    private static final String PRODUCT = "pro.monthly";

    @Autowired EntitlementStateService service;
    @Autowired EntitlementMapper entitlementMapper;
    @Autowired com.innercosmos.mapper.EntitlementEventMapper entitlementEventMapper;

    private static LocalDateTime at(String isoUtc) {
        return LocalDateTime.parse(isoUtc);
    }

    @Test
    void paymentActivatesThenStacksRenewalsWithoutOverlappingPaidTime() {
        long user = uniqueUser();
        String order = "O-" + user;
        Entitlement first = service.onPaymentSucceeded(user, PRODUCT, "wechatpay", order,
                "evt-act-" + user, at("2026-01-01T04:00:00"));
        assertEquals(Entitlement.ACTIVE, first.state);
        assertEquals(at("2026-02-01T04:00:00"), first.periodEnd, "one month, UTC-normalized");

        // Renewal BEFORE the period ends: the new period stacks, paid time never overlaps.
        Entitlement renewed = service.onPaymentSucceeded(user, PRODUCT, "alipay", order + "-2",
                "evt-ren-" + user, at("2026-01-20T04:00:00"));
        assertEquals(at("2026-03-01T04:00:00"), renewed.periodEnd,
                "renewal extends from period end, not from payment time");
        assertTrue(Boolean.TRUE.equals(renewed.autoRenew));
    }

    @Test
    void monthEndRenewalsClampOnTheShanghaiCalendar() {
        long user = uniqueUser();
        // A period starting Jan 31 12:00 +08 renews to Feb 28 12:00 +08 — never into March.
        Entitlement entitlement = service.onPaymentSucceeded(user, PRODUCT, "wechatpay",
                "O-ME-" + user, "evt-me-" + user, at("2026-01-31T04:00:00"));
        assertEquals(at("2026-02-28T04:00:00"), entitlement.periodEnd,
                "Jan 31 + 1 month clamps to Feb 28 (时区/月末)");
        Entitlement stacked = service.onPaymentSucceeded(user, PRODUCT, "wechatpay",
                "O-ME2-" + user, "evt-me2-" + user, at("2026-02-20T04:00:00"));
        assertEquals(at("2026-03-28T04:00:00"), stacked.periodEnd,
                "Feb 28 + 1 month stays on the 28th");
    }

    @Test
    void duplicatePaymentAndChannelNotificationsAcksNeverDoubleGrants() {
        long user = uniqueUser();
        service.onPaymentSucceeded(user, PRODUCT, "alipay", "O-DUP-" + user,
                "evt-dup-" + user, at("2026-03-01T04:00:00"));
        Entitlement duplicate = service.onPaymentSucceeded(user, PRODUCT, "alipay",
                "O-DUP-" + user, "evt-dup-" + user, at("2026-03-01T04:00:00"));
        assertEquals(at("2026-04-01T04:00:00"), duplicate.periodEnd,
                "the duplicated payment notification must not extend the period again");
        long auditRows = countAudit("evt-dup-" + user);
        assertEquals(1, auditRows, "one transition recorded for one channel notification id");

        // Same discipline for subscription notifications.
        service.onChannelSubscriptionState(user, PRODUCT, "wechatpay", "sub-dup-" + user,
                ChannelSubscriptionState.RENEWED, at("2026-03-15T04:00:00"));
        service.onChannelSubscriptionState(user, PRODUCT, "wechatpay", "sub-dup-" + user,
                ChannelSubscriptionState.RENEWED, at("2026-03-15T04:00:00"));
        assertEquals(1, countAudit("sub-dup-" + user));
    }

    @Test
    void refundRevokesStickilyAndRestoreCannotLaunderItButRepurchaseCan() {
        long user = uniqueUser();
        service.onPaymentSucceeded(user, PRODUCT, "wechatpay", "O-RV-" + user,
                "evt-rv-pay-" + user, at("2026-04-01T04:00:00"));
        Entitlement revoked = service.onRefundSucceeded(user, PRODUCT,
                "evt-rv-refund-" + user, at("2026-04-02T04:00:00"));
        assertEquals(Entitlement.REVOKED, revoked.state);
        assertFalse(Boolean.TRUE.equals(revoked.autoRenew));

        // 恢复购买 on a refund-revoked row is rejected — only a new purchase revives.
        BusinessException rejected = assertThrows(BusinessException.class,
                () -> service.onChannelSubscriptionState(user, PRODUCT, "wechatpay",
                        "sub-restore-" + user, ChannelSubscriptionState.RESTORED,
                        at("2026-04-03T04:00:00")));
        assertEquals("CONFLICT", rejected.code);
        // The rejected restore left no audit row and no state change.
        assertEquals(0, countAudit("sub-restore-" + user));

        // A new payment (re-purchase) starts a fresh ACTIVE period.
        Entitlement repurchased = service.onPaymentSucceeded(user, PRODUCT, "alipay",
                "O-RV2-" + user, "evt-rv-pay2-" + user, at("2026-04-05T04:00:00"));
        assertEquals(Entitlement.ACTIVE, repurchased.state);
        assertEquals(at("2026-05-05T04:00:00"), repurchased.periodEnd);

        // A refund for an entitlement that never existed invents nothing.
        assertNull(service.onRefundSucceeded(uniqueUser(), PRODUCT,
                "evt-rv-none-" + user, at("2026-04-06T04:00:00")));
    }

    @Test
    void channelLifecyclePendingTrialRenewGraceCancelExpireAllNormalize() {
        long user = uniqueUser();
        Entitlement pending = service.onChannelSubscriptionState(user, PRODUCT, "wechatpay",
                "sub-p-" + user, ChannelSubscriptionState.PURCHASE_PENDING,
                at("2026-05-01T04:00:00"));
        assertEquals(Entitlement.PENDING_PAYMENT, pending.state);
        assertEquals(pending.periodStart, pending.periodEnd, "pending owes nothing");

        Entitlement trial = service.onChannelSubscriptionState(user, PRODUCT, "wechatpay",
                "sub-t-" + user, ChannelSubscriptionState.TRIAL_STARTED, at("2026-05-01T04:00:00"));
        assertEquals(Entitlement.TRIAL, trial.state);
        assertEquals(at("2026-05-15T04:00:00"), trial.periodEnd, "14-day sandbox trial contract");

        Entitlement renewed = service.onChannelSubscriptionState(user, PRODUCT, "wechatpay",
                "sub-r-" + user, ChannelSubscriptionState.RENEWED, at("2026-05-14T04:00:00"));
        assertEquals(Entitlement.ACTIVE, renewed.state);
        assertEquals(at("2026-06-15T04:00:00"), renewed.periodEnd, "trial conversion stacks from trial end");

        Entitlement grace = service.onChannelSubscriptionState(user, PRODUCT, "wechatpay",
                "sub-g-" + user, ChannelSubscriptionState.GRACE_ENTERED, at("2026-06-15T04:00:00"));
        assertEquals(Entitlement.GRACE_PERIOD, grace.state);

        Entitlement cancelled = service.onChannelSubscriptionState(user, PRODUCT, "wechatpay",
                "sub-c-" + user, ChannelSubscriptionState.CANCELLED, at("2026-06-20T04:00:00"));
        assertEquals(Entitlement.CANCELLED, cancelled.state);
        assertEquals(at("2026-06-15T04:00:00"), cancelled.periodEnd,
                "cancellation keeps the paid period end (不收回已付时间)");
        assertFalse(Boolean.TRUE.equals(cancelled.autoRenew));
        assertTrue(Boolean.TRUE.equals(cancelled.cancelAtPeriodEnd));

        Entitlement expired = service.onChannelSubscriptionState(user, PRODUCT, "wechatpay",
                "sub-e-" + user, ChannelSubscriptionState.EXPIRED, at("2026-06-16T04:00:00"));
        assertEquals(Entitlement.EXPIRED, expired.state);

        // Renewal/grace/cancel/expire for an unknown entitlement is rejected, not invented.
        BusinessException unknown = assertThrows(BusinessException.class,
                () -> service.onChannelSubscriptionState(uniqueUser(), PRODUCT, "wechatpay",
                        "sub-unknown-" + user, ChannelSubscriptionState.RENEWED,
                        at("2026-06-01T04:00:00")));
        assertEquals("NOT_FOUND", unknown.code);
        assertEquals(0, countAudit("sub-unknown-" + user));
    }

    @Test
    void restoreRevivesAValidPeriodAndRefusesADeadOne() {
        long user = uniqueUser();
        service.onPaymentSucceeded(user, PRODUCT, "wechatpay", "O-RS-" + user,
                "evt-rs-pay-" + user, at("2027-01-01T04:00:00"));
        // Cross-device re-login: restore of a still-valid period returns ACTIVE.
        Entitlement restored = service.onChannelSubscriptionState(user, PRODUCT, "wechatpay",
                "sub-rs-" + user, ChannelSubscriptionState.RESTORED, at("2027-01-10T04:00:00"));
        assertEquals(Entitlement.ACTIVE, restored.state);

        // A period that already ended restores to EXPIRED — restore grants no free time.
        long other = uniqueUser();
        service.onPaymentSucceeded(other, PRODUCT, "wechatpay", "O-RS2-" + other,
                "evt-rs2-pay-" + other, at("2026-01-01T04:00:00"));
        Entitlement dead = service.onChannelSubscriptionState(other, PRODUCT, "wechatpay",
                "sub-rs2-" + other, ChannelSubscriptionState.RESTORED,
                at("2026-06-10T04:00:00"));
        assertEquals(Entitlement.EXPIRED, dead.state);
    }

    @Test
    void userCancellationKeepsPaidTimeAndSnapshotLazilyExpiresStalePeriods() {
        long user = uniqueUser();
        service.onPaymentSucceeded(user, PRODUCT, "alipay", "O-UC-" + user,
                "evt-uc-pay-" + user, at("2027-01-01T04:00:00"));
        Entitlement cancelled = service.cancelByUser(user, PRODUCT);
        assertEquals(Entitlement.CANCELLED, cancelled.state);
        assertEquals(at("2027-02-01T04:00:00"), cancelled.periodEnd);
        assertTrue(Boolean.TRUE.equals(cancelled.cancelAtPeriodEnd));

        List<EntitlementView> stillEntitled = service.snapshot(user);
        assertEquals(1, stillEntitled.size());
        assertEquals(Entitlement.CANCELLED, stillEntitled.get(0).state());
        assertEquals(at("2027-02-01T04:00:00"), stillEntitled.get(0).quotaResetsAt(),
                "quota window resets at period end");

        // A period long past, never renewed: snapshot must not report it as entitled.
        long stale = uniqueUser();
        service.onPaymentSucceeded(stale, PRODUCT, "alipay", "O-ST-" + stale,
                "evt-st-pay-" + stale, at("2026-01-01T04:00:00"));
        assertEquals(Entitlement.EXPIRED, service.snapshot(stale).get(0).state(),
                "lazy expiry turns stale periods into EXPIRED");

        // Cancelling an unknown product is a clean NOT_FOUND, never a silent no-op.
        BusinessException missing = assertThrows(BusinessException.class,
                () -> service.cancelByUser(uniqueUser(), PRODUCT));
        assertEquals("NOT_FOUND", missing.code);
    }

    @Test
    void safetyCorrectionExportAndDeletionAreNeverPayGated() {
        // 安全/纠正/导出删除永不付费解锁 — structural, enforced by disjoint catalogs.
        for (String capability : EntitlementGates.NEVER_PAID_GATED) {
            assertFalse(EntitlementGates.PAID_GATED.contains(capability),
                    capability + " must never be pay-gated");
        }
        // The safety-family capabilities named by the blueprint are all in the never set.
        for (String required : List.of("safety.crisis_interception", "portrait.correction",
                "data.export", "data.deletion", "account.cancellation")) {
            assertTrue(EntitlementGates.NEVER_PAID_GATED.contains(required));
        }
        // And the machine's unified view exists per user (empty snapshot for a fresh user).
        assertNotNull(service.snapshot(uniqueUser()));
    }

    private long countAudit(String notificationId) {
        return entitlementEventMapper.selectCount(new QueryWrapper<EntitlementEvent>()
                .eq("channel_notification_id", notificationId));
    }

    /** Distinct test users keep the UNIQUE(user_id, product_id) rows independent. */
    private static final java.util.concurrent.atomic.AtomicLong USER_SEQ =
            new java.util.concurrent.atomic.AtomicLong(900_000_000L);

    private static long uniqueUser() {
        return USER_SEQ.incrementAndGet();
    }
}
