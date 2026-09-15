package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * CP-45 server-side order fact: what the app asked the user to pay (expected amount,
 * product, channel), never what a callback claims. Payment outcomes live exclusively in
 * the append-only {@code tb_payment_event} ledger; this row is the validation authority
 * the callback pipeline checks channel claims against.
 *
 * <p>§2-21 residual: {@code priceVersionId} pins the immutable price version the order
 * was priced at (a later price change never rewrites it), and {@code expiresAt} is the
 * order TTL deadline — past it, an unpaid CREATED order flips to the EXPIRED terminal
 * and late callbacks are refused as facts, never fabricated into a success.
 */
@TableName("tb_payment_order")
public class PaymentOrder extends BaseEntity {
    public String orderId;
    public Long userId;
    public String productId;
    public String channel;
    public Long expectedAmountCents;
    public String currency;
    public Long priceVersionId;
    public String status;
    public LocalDateTime expiresAt;
}
