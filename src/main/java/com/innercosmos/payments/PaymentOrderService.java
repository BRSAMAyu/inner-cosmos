package com.innercosmos.payments;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.PaymentOrder;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.PaymentOrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * CP-45 server-side order catalog. Orders are created ONLY here — by the app, with the
 * expected amount/product/channel the user saw — never by a callback. The callback
 * pipeline validates every channel claim against this catalog (未知订单/金额漂移/渠道
 * 不符全部拒收或落 DISPUTED), so a correctly-signed callback can no longer invent an
 * amount we never asked for.
 *
 * <p>Pricing authority (§2-21 residual): {@link PriceVersionService}'s append-only price
 * table. Order creation pins the current ACTIVE version's id and amount onto the order
 * row — a later price change never rewrites an existing order (旧订单引用旧版本可追溯),
 * and callback amount validation keeps checking the pinned expectation.
 *
 * <p>Order expiry: every new order carries expiresAt = creation + the configured TTL
 * (default 2h; legacy rows have NULL = never expires). Expiry is terminal and reached
 * two idempotent ways: lazily — {@link #find} flips an overdue CREATED order to EXPIRED
 * on any callback/query path — and by the scheduled batch sweep {@link #expireOverdue}.
 * Both are conditional on status='CREATED', so a late callback and the sweep racing on
 * the same order can never flip it twice, and a payment that arrives late is REFUSED as
 * a fact (过期不伪造支付成功) — see ChannelCallbackIngestService.
 */
@Service
public class PaymentOrderService {

    private static final Set<String> CATALOG = Set.of("pro.monthly");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final PaymentOrderMapper mapper;
    private final PriceVersionService prices;
    private final Duration defaultTtl;
    private final Clock clock;

    @Autowired
    public PaymentOrderService(PaymentOrderMapper mapper, PriceVersionService prices,
            @Value("${inner-cosmos.payments.order-expiry.default-ttl:PT2H}") Duration defaultTtl) {
        this(mapper, prices, defaultTtl, Clock.systemUTC());
    }

    /** Test wiring: a deterministic clock and TTL for expiry-contract tests. */
    public PaymentOrderService(PaymentOrderMapper mapper, PriceVersionService prices,
            Duration defaultTtl, Clock clock) {
        this.mapper = mapper;
        this.prices = prices;
        this.defaultTtl = defaultTtl;
        this.clock = clock;
    }

    /** The only products that may be ordered; anything else is a catalog error. */
    public static boolean inCatalog(String productId) {
        return CATALOG.contains(productId);
    }

    /**
     * Creates the order fact the callback pipeline will validate against: the current
     * ACTIVE price version is pinned (id + amount + currency), so the order's expectation
     * is frozen at creation no matter how the price moves afterwards.
     */
    public PaymentOrder createOrder(long userId, String productId, String channel) {
        if (!inCatalog(productId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "unknown product " + productId);
        }
        var price = prices.currentActiveOrSeed(productId);
        PaymentOrder order = new PaymentOrder();
        order.orderId = "IC" + STAMP.format(LocalDateTime.now(clock).atZone(ZoneOffset.UTC))
                + "-" + hex8();
        order.userId = userId;
        order.productId = productId;
        order.channel = channel;
        order.priceVersionId = price.id;
        order.expectedAmountCents = price.amountCents;
        order.currency = price.currency;
        order.status = "CREATED";
        order.expiresAt = LocalDateTime.now(clock).plus(defaultTtl);
        mapper.insert(order);
        return order;
    }

    /**
     * Lookup by channel order id, with lazy expiry folded in: if the order is CREATED and
     * its deadline has passed, it flips to the EXPIRED terminal here (conditional update —
     * idempotent under a racing sweep or callback), and the caller sees the fresh state.
     */
    public PaymentOrder find(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return null;
        }
        PaymentOrder order = mapper.selectOne(new QueryWrapper<PaymentOrder>()
                .eq("order_id", orderId));
        if (order != null) {
            expireIfOverdue(order);
        }
        return order;
    }

    /** Flips an overdue CREATED order to EXPIRED; returns true if this call did the flip. */
    public boolean expireIfOverdue(PaymentOrder order) {
        if (order == null || !"CREATED".equals(order.status) || order.expiresAt == null) {
            return false;
        }
        if (order.expiresAt.isAfter(LocalDateTime.now(clock))) {
            return false;
        }
        if (flipToExpired(order.orderId)) {
            // The caller holds this row; reflect the terminal state it now persists in.
            order.status = "EXPIRED";
            return true;
        }
        return false;
    }

    /**
     * Scheduled batch sweep: expires every overdue CREATED order in one conditional
     * statement. Returns how many rows flipped (0 = nothing due / already terminal).
     */
    public int expireOverdue() {
        return mapper.update(null, new UpdateWrapper<PaymentOrder>()
                .eq("status", "CREATED")
                .isNotNull("expires_at")
                .le("expires_at", LocalDateTime.now(clock))
                .set("status", "EXPIRED"));
    }

    /** Conditional flip: only ever moves CREATED → EXPIRED, so it cannot fire twice. */
    private boolean flipToExpired(String orderId) {
        int flipped = mapper.update(null, new UpdateWrapper<PaymentOrder>()
                .eq("order_id", orderId)
                .eq("status", "CREATED")
                .set("status", "EXPIRED"));
        return flipped > 0;
    }

    private static String hex8() {
        return String.format("%08x", RANDOM.nextInt());
    }
}
