package com.innercosmos.payments;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.PaymentOrder;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.PaymentOrderMapper;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
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
 * <p>Pricing authority: {@link #priceCents(String)} is the single price table for the
 * catalog products (CNY minor units). Real channel prepay (微信下单/支付宝预创建) is
 * operator-gated wiring on top of this order fact.
 */
@Service
public class PaymentOrderService {

    private static final Set<String> CATALOG = Set.of("pro.monthly");
    private static final long PRO_MONTHLY_CENTS = 2500L; // ¥25.00/月（沙箱定价，正式定价走价格版本表）
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final PaymentOrderMapper mapper;

    public PaymentOrderService(PaymentOrderMapper mapper) {
        this.mapper = mapper;
    }

    /** The only products that may be ordered; anything else is a catalog error. */
    public static boolean inCatalog(String productId) {
        return CATALOG.contains(productId);
    }

    public static long priceCents(String productId) {
        if (!inCatalog(productId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "unknown product " + productId);
        }
        return PRO_MONTHLY_CENTS;
    }

    /** Creates the order fact the callback pipeline will validate against. */
    public PaymentOrder createOrder(long userId, String productId, String channel) {
        if (!inCatalog(productId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "unknown product " + productId);
        }
        PaymentOrder order = new PaymentOrder();
        order.orderId = "IC" + STAMP.format(LocalDateTime.now(ZoneOffset.UTC)
                .atZone(ZoneOffset.UTC)) + "-" + hex8();
        order.userId = userId;
        order.productId = productId;
        order.channel = channel;
        order.expectedAmountCents = priceCents(productId);
        order.currency = "CNY";
        order.status = "CREATED";
        mapper.insert(order);
        return order;
    }

    public PaymentOrder find(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return null;
        }
        return mapper.selectOne(new QueryWrapper<PaymentOrder>().eq("order_id", orderId));
    }

    private static String hex8() {
        return String.format("%08x", RANDOM.nextInt());
    }
}
