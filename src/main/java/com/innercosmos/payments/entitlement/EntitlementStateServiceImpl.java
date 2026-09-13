package com.innercosmos.payments.entitlement;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.Entitlement;
import com.innercosmos.entity.EntitlementEvent;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.EntitlementEventMapper;
import com.innercosmos.mapper.EntitlementMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * CP-46 unified entitlement state machine implementation. Design invariants:
 *
 * <ul>
 *   <li><b>Dedup first, mutate second</b> — every public input is keyed by a channel
 *       notification id; a duplicate acks the existing audit row before any state is
 *       touched, and the insert races are caught by UNIQUE constraints (concurrent pods
 *       cannot double-grant: the loser re-reads the winner's committed row).</li>
 *   <li><b>Refund revocation is sticky</b> — REVOKED survives restore notifications
 *       (退款后撤销); only a NEW payment (re-purchase) revives it.</li>
 *   <li><b>Cancellation keeps the paid period</b> — CANCELLED rows stay entitled until
 *       period_end (取消入口与续费提醒清晰：到期即停，不再扣费).</li>
 *   <li><b>Month-end clamping in Asia/Shanghai</b> — renewal month math runs on the
 *       mainland calendar: a Jan-31 period renews to Feb-28, never Mar-2 (时区/月末).</li>
 *   <li><b>Lazy expiry</b> — snapshot() expires stale non-revoked periods instead of
 *       letting an untouched past period keep reading as entitled.</li>
 * </ul>
 */
@Service
public class EntitlementStateServiceImpl implements EntitlementStateService {

    /** Mainland subscription calendar: renewal month math runs in Asia/Shanghai. */
    private static final ZoneOffset SHANGHAI = ZoneOffset.ofHours(8);
    /**
     * Sandbox trial contract: 14 days. Real channel trial lengths arrive in the notify
     * payload; mirroring them is operator-gated wiring (no channel contract in hand).
     */
    private static final Duration TRIAL_PERIOD = Duration.ofDays(14);

    private final EntitlementMapper entitlementMapper;
    private final EntitlementEventMapper eventMapper;

    public EntitlementStateServiceImpl(EntitlementMapper entitlementMapper,
                                       EntitlementEventMapper eventMapper) {
        this.entitlementMapper = entitlementMapper;
        this.eventMapper = eventMapper;
    }

    @Override
    public Entitlement onPaymentSucceeded(long userId, String productId, String channel,
                                          String orderId, String providerEventId,
                                          LocalDateTime occurredAt) {
        requireText(providerEventId, "providerEventId");
        if (alreadyApplied(providerEventId)) {
            return find(userId, productId); // duplicate payment notification: ack only
        }
        Entitlement existing = find(userId, productId);
        Entitlement entitlement;
        String from;
        if (existing == null) {
            Entitlement fresh = new Entitlement();
            fresh.userId = userId;
            fresh.productId = productId;
            fresh.channel = channel;
            fresh.channelOrderId = orderId;
            fresh.periodStart = occurredAt;
            fresh.periodEnd = nextPeriodEnd(occurredAt);
            fresh.autoRenew = true;
            fresh.cancelAtPeriodEnd = false;
            fresh.state = Entitlement.ACTIVE;
            Entitlement raced = insertOrAck(fresh, userId, productId);
            if (raced == null) {
                audit(fresh, providerEventId, "ACTIVATE", null, Entitlement.ACTIVE, occurredAt);
                return fresh;
            }
            existing = raced; // concurrent first-grant: fall through and treat as renewal
        }
        entitlement = existing;
        from = entitlement.state;
        LocalDateTime base = switch (entitlement.state) {
            case Entitlement.ACTIVE, Entitlement.GRACE_PERIOD, Entitlement.CANCELLED ->
                    // stacked renewal: paid time never overlaps; converting/re-purchasing rows start now
                    entitlement.periodEnd != null && entitlement.periodEnd.isAfter(occurredAt)
                            ? entitlement.periodEnd : occurredAt;
            default -> occurredAt; // PENDING_PAYMENT / TRIAL convert; EXPIRED / REVOKED re-purchase
        };
        entitlement.periodEnd = nextPeriodEnd(base);
        entitlement.state = Entitlement.ACTIVE;
        entitlement.autoRenew = true;
        entitlement.cancelAtPeriodEnd = false;
        entitlement.channel = channel;
        entitlement.channelOrderId = orderId;
        entitlementMapper.updateById(entitlement);
        audit(entitlement, providerEventId, "RENEW", from, Entitlement.ACTIVE, occurredAt);
        return entitlement;
    }

    @Override
    public Entitlement onRefundSucceeded(long userId, String productId, String providerEventId,
                                         LocalDateTime occurredAt) {
        requireText(providerEventId, "providerEventId");
        Entitlement entitlement = find(userId, productId);
        if (alreadyApplied(providerEventId)) {
            return entitlement;
        }
        if (entitlement == null) {
            // Refund for a fact we never entitled (refund raced activation): nothing to
            // revoke and nothing to invent — the payment ledger already holds the fact.
            return null;
        }
        String from = entitlement.state;
        entitlement.state = Entitlement.REVOKED;
        entitlement.autoRenew = false;
        entitlementMapper.updateById(entitlement);
        audit(entitlement, providerEventId, "REVOKE_REFUND", from, Entitlement.REVOKED, occurredAt);
        return entitlement;
    }

    @Override
    public Entitlement onChannelSubscriptionState(long userId, String productId, String channel,
                                                  String notificationId,
                                                  ChannelSubscriptionState channelState,
                                                  LocalDateTime occurredAt) {
        requireText(notificationId, "notificationId");
        if (channelState == null) {
            // Unknown future channel state: fail closed — no transition, no audit row.
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "unknown channel subscription state — no transition applied");
        }
        Entitlement entitlement = find(userId, productId);
        if (alreadyApplied(notificationId)) {
            return entitlement;
        }
        if (entitlement == null && channelState != ChannelSubscriptionState.PURCHASE_PENDING
                && channelState != ChannelSubscriptionState.TRIAL_STARTED) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "no entitlement for " + productId + " — channel state rejected");
        }
        if (entitlement == null) {
            entitlement = ensureRow(userId, productId, channel, occurredAt);
        }
        String from = entitlement.state;
        switch (channelState) {
            case PURCHASE_PENDING -> {
                // Zero-width placeholder period: nothing is owed until the payment fact lands.
                entitlement.state = Entitlement.PENDING_PAYMENT;
                entitlement.periodStart = occurredAt;
                entitlement.periodEnd = occurredAt;
                entitlement.autoRenew = true;
                entitlement.cancelAtPeriodEnd = false;
            }
            case TRIAL_STARTED -> {
                entitlement.state = Entitlement.TRIAL;
                entitlement.periodStart = occurredAt;
                entitlement.periodEnd = occurredAt.plus(TRIAL_PERIOD);
                entitlement.autoRenew = true;
                entitlement.cancelAtPeriodEnd = false;
            }
            case RENEWED -> {
                LocalDateTime base = entitlement.periodEnd != null
                        && entitlement.periodEnd.isAfter(occurredAt) ? entitlement.periodEnd : occurredAt;
                entitlement.periodEnd = nextPeriodEnd(base);
                entitlement.state = Entitlement.ACTIVE;
                entitlement.autoRenew = true;
                entitlement.cancelAtPeriodEnd = false;
            }
            case GRACE_ENTERED -> entitlement.state = Entitlement.GRACE_PERIOD;
            case CANCELLED -> {
                // Entitled until period end; renewal is off. Cancellation never claws
                // back paid time — the view shows 保留至 period_end，之后不再扣费.
                entitlement.state = Entitlement.CANCELLED;
                entitlement.autoRenew = false;
                entitlement.cancelAtPeriodEnd = true;
            }
            case EXPIRED -> {
                entitlement.state = Entitlement.EXPIRED;
                entitlement.autoRenew = false;
            }
            case RESTORED -> {
                if (Entitlement.REVOKED.equals(entitlement.state)) {
                    // 退款撤销不可被恢复通知洗白 — only a new purchase revives it.
                    throw new BusinessException(ErrorCode.CONFLICT,
                            "refund-revoked entitlement cannot be restored; a new purchase is required");
                }
                boolean stillValid = entitlement.periodEnd != null
                        && entitlement.periodEnd.isAfter(LocalDateTime.now(ZoneOffset.UTC));
                entitlement.state = stillValid ? Entitlement.ACTIVE : Entitlement.EXPIRED;
            }
        }
        entitlement.channel = channel;
        entitlementMapper.updateById(entitlement);
        audit(entitlement, notificationId, channelState.name(), from, entitlement.state, occurredAt);
        return entitlement;
    }

    @Override
    public Entitlement cancelByUser(long userId, String productId) {
        Entitlement entitlement = find(userId, productId);
        if (entitlement == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "no entitlement for " + productId);
        }
        if (Entitlement.REVOKED.equals(entitlement.state)) {
            throw new BusinessException(ErrorCode.CONFLICT, "already revoked by refund");
        }
        String from = entitlement.state;
        boolean stillEntitled = entitlement.periodEnd != null
                && entitlement.periodEnd.isAfter(LocalDateTime.now(ZoneOffset.UTC));
        entitlement.state = stillEntitled ? Entitlement.CANCELLED : Entitlement.EXPIRED;
        entitlement.autoRenew = false;
        entitlement.cancelAtPeriodEnd = true;
        entitlementMapper.updateById(entitlement);
        // One cancel per entitlement: repeating the request acks instead of re-auditing.
        audit(entitlement, "user:cancel:" + entitlement.id, "USER_CANCEL",
                from, entitlement.state, LocalDateTime.now(ZoneOffset.UTC));
        return entitlement;
    }

    @Override
    public List<EntitlementView> snapshot(long userId) {
        List<EntitlementView> views = new ArrayList<>();
        for (Entitlement entitlement : entitlementMapper.selectList(
                new QueryWrapper<Entitlement>().eq("user_id", userId).orderByAsc("product_id"))) {
            if (!Entitlement.REVOKED.equals(entitlement.state)
                    && entitlement.periodEnd != null
                    && entitlement.periodEnd.isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
                // Lazy expiry: a period that ended without renewal is not entitled, no
                // matter which non-revoked state the row still carries.
                entitlement.state = Entitlement.EXPIRED;
                entitlement.autoRenew = false;
                entitlementMapper.updateById(entitlement);
            }
            views.add(new EntitlementView(entitlement.productId, entitlement.state,
                    entitlement.periodStart, entitlement.periodEnd,
                    Boolean.TRUE.equals(entitlement.autoRenew),
                    Boolean.TRUE.equals(entitlement.cancelAtPeriodEnd),
                    entitlement.periodEnd));
        }
        return views;
    }

    // ---------- helpers ----------

    private Entitlement find(long userId, String productId) {
        return entitlementMapper.selectOne(new QueryWrapper<Entitlement>()
                .eq("user_id", userId).eq("product_id", productId));
    }

    /** Placeholder PENDING row for channel states that arrive before any payment fact. */
    private Entitlement ensureRow(long userId, String productId, String channel,
                                  LocalDateTime occurredAt) {
        Entitlement existing = find(userId, productId);
        if (existing != null) {
            return existing;
        }
        Entitlement fresh = new Entitlement();
        fresh.userId = userId;
        fresh.productId = productId;
        fresh.channel = channel;
        fresh.periodStart = occurredAt;
        fresh.periodEnd = occurredAt;
        fresh.autoRenew = true;
        fresh.cancelAtPeriodEnd = false;
        fresh.state = Entitlement.PENDING_PAYMENT;
        Entitlement raced = insertOrAck(fresh, userId, productId);
        return raced != null ? raced : fresh;
    }

    /** @return null on success, or the concurrent winner when the UNIQUE row already landed. */
    private Entitlement insertOrAck(Entitlement fresh, long userId, String productId) {
        try {
            entitlementMapper.insert(fresh);
            return null;
        } catch (DuplicateKeyException raced) {
            return find(userId, productId);
        }
    }

    private boolean alreadyApplied(String notificationId) {
        return eventMapper.selectCount(new QueryWrapper<EntitlementEvent>()
                .eq("channel_notification_id", notificationId)) > 0;
    }

    private void audit(Entitlement entitlement, String notificationId, String transition,
                       String fromState, String toState, LocalDateTime occurredAt) {
        EntitlementEvent event = new EntitlementEvent();
        event.channelNotificationId = notificationId;
        event.entitlementId = entitlement.id;
        event.userId = entitlement.userId;
        event.transition = transition;
        event.fromState = fromState == null ? "NONE" : fromState;
        event.toState = toState;
        event.occurredAt = occurredAt;
        try {
            eventMapper.insert(event);
        } catch (DuplicateKeyException raced) {
            // Concurrent pod applied the same notification first — its transition is the
            // committed one; this one is the idempotent ack.
        }
    }

    /** Renews one month forward on the Asia/Shanghai calendar (Jan 31 → Feb 28, 时区/月末). */
    private static LocalDateTime nextPeriodEnd(LocalDateTime baseUtc) {
        ZonedDateTime renewed = baseUtc.toInstant(ZoneOffset.UTC)
                .atZone(SHANGHAI).plusMonths(1);
        return renewed.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "missing " + name);
        }
    }
}
