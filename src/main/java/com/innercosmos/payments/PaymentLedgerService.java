package com.innercosmos.payments;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.PaymentEvent;
import com.innercosmos.mapper.PaymentEventMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CP-47 append-only payment ledger. Recording is idempotent per provider_event_id (a
 * duplicated callback acks the existing row and never double-credits), and reconciliation
 * NETS payments minus refunds per order — arithmetic that is insensitive to arrival order,
 * so an out-of-order refund (before its payment lands) or a late payment (after a refund)
 * both reconcile to the same truth. Mismatched nets are flagged DISPUTED per row, never
 * silently absorbed; the ledger is facts only — entitlement grants sit above it.
 */
@Service
public class PaymentLedgerService {

    private final PaymentEventMapper mapper;

    public PaymentLedgerService(PaymentEventMapper mapper) {
        this.mapper = mapper;
    }

    /** Idempotent record: returns the persisted row; a duplicate provider_event_id is a no-op ack. */
    public PaymentEvent record(String providerEventId, String provider, String orderId,
                               String eventType, long amountCents, LocalDateTime occurredAt) {
        return record(providerEventId, provider, orderId, eventType, amountCents, occurredAt,
                "RECORDED");
    }

    /**
     * CP-45 drift recording: a verified callback whose amount contradicts the order catalog
     * still HAPPENED (the channel moved money we did not ask for) — it is recorded as a
     * DISPUTED fact for reconciliation, never silently absorbed and never acknowledged as a
     * clean payment. Same idempotency per provider_event_id as {@link #record}.
     */
    public PaymentEvent recordDisputed(String providerEventId, String provider, String orderId,
                                       String eventType, long amountCents,
                                       LocalDateTime occurredAt) {
        return record(providerEventId, provider, orderId, eventType, amountCents, occurredAt,
                "DISPUTED");
    }

    /**
     * §2-21 refused-late-callback recording: a payment/refund notify for an order already
     * in the EXPIRED terminal is refused, but the refusal itself is a fact — recorded as
     * an EXPIRED_ORDER_CALLBACK row with status REJECTED so reconciliation can see money
     * the channel says moved against an order we already closed. Informational by
     * construction: {@link #orderNetCents} never counts this event type. Same idempotency
     * per provider_event_id as {@link #record} (a channel retry acks, never double-rows).
     */
    public PaymentEvent recordRejected(String providerEventId, String provider, String orderId,
                                       String eventType, long amountCents,
                                       LocalDateTime occurredAt) {
        return record(providerEventId, provider, orderId, eventType, amountCents, occurredAt,
                "REJECTED");
    }

    private PaymentEvent record(String providerEventId, String provider, String orderId,
                                String eventType, long amountCents, LocalDateTime occurredAt,
                                String status) {
        PaymentEvent existing = mapper.selectOne(new QueryWrapper<PaymentEvent>()
                .eq("provider_event_id", providerEventId));
        if (existing != null) return existing;
        PaymentEvent event = new PaymentEvent();
        event.providerEventId = providerEventId;
        event.provider = provider;
        event.orderId = orderId;
        event.eventType = eventType;
        event.amountCents = amountCents;
        event.occurredAt = occurredAt;
        event.status = status;
        try {
            mapper.insert(event);
            return event;
        } catch (org.springframework.dao.DuplicateKeyException raced) {
            // Another pod inserted the same provider event first — that is the idempotent ack.
            return mapper.selectOne(new QueryWrapper<PaymentEvent>()
                    .eq("provider_event_id", providerEventId));
        }
    }

    /**
     * Net cents for an order: payments minus refunds, arrival-order-insensitive. Only
     * money-movement facts count — informational rows (e.g. a refused EXPIRED_ORDER_
     * CALLBACK) are skipped, so a refused late callback can never dent the net.
     */
    public long orderNetCents(String orderId) {
        List<PaymentEvent> rows = mapper.selectList(new QueryWrapper<PaymentEvent>()
                .eq("order_id", orderId));
        long net = 0;
        for (PaymentEvent row : rows) {
            switch (row.eventType == null ? "" : row.eventType) {
                case "PAYMENT_SUCCEEDED" -> net += row.amountCents;
                case "REFUND_SUCCEEDED" -> net -= row.amountCents;
                default -> { /* informational fact, not money movement */ }
            }
        }
        return net;
    }

    /**
     * Reconciles an order against the expected net; mismatches flag every DISPUTED row of
     * the order (visible for the CP-47 "对账异常暂停扩张" rule) instead of hiding drift.
     */
    public boolean reconcile(String orderId, long expectedNetCents) {
        boolean matches = orderNetCents(orderId) == expectedNetCents;
        if (!matches) {
            for (PaymentEvent row : mapper.selectList(new QueryWrapper<PaymentEvent>()
                    .eq("order_id", orderId).eq("status", "RECORDED"))) {
                row.status = "DISPUTED";
                mapper.updateById(row);
            }
        }
        return matches;
    }
}
