package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** CP-47 append-only payment fact; no signatures, secrets or payer identity stored. */
@TableName("tb_payment_event")
public class PaymentEvent extends BaseEntity {
    public String providerEventId;
    public String provider;
    public String orderId;
    public String eventType;
    public Long amountCents;
    public String currency;
    public String status;
    public LocalDateTime occurredAt;
    public LocalDateTime receivedAt;
}
