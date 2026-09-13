package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * CP-45 server-side order fact: what the app asked the user to pay (expected amount,
 * product, channel), never what a callback claims. Payment outcomes live exclusively in
 * the append-only {@code tb_payment_event} ledger; this row is the validation authority
 * the callback pipeline checks channel claims against.
 */
@TableName("tb_payment_order")
public class PaymentOrder extends BaseEntity {
    public String orderId;
    public Long userId;
    public String productId;
    public String channel;
    public Long expectedAmountCents;
    public String currency;
    public String status;
}
