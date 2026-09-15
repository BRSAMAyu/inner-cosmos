package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * CP-45 residual (§2-21): one immutable price version of a catalog product. A price
 * change inserts a NEW ACTIVE row and flips the previous row to RETIRED — the amount of
 * an existing version row is never edited in place, so every order can always trace the
 * exact version (and amount) it was priced at (旧订单引用旧版本可追溯). Currency stays
 * CNY minor units, same as {@link PaymentOrder#expectedAmountCents}.
 */
@TableName("tb_price_version")
public class PriceVersion extends BaseEntity {
    public String productId;
    public Integer version;
    public Long amountCents;
    public String currency;
    /** ACTIVE = the version orders price at right now; RETIRED = superseded, immutable. */
    public String status;
    public LocalDateTime effectiveFrom;
    /** When this version stopped being ACTIVE; NULL while it still is. */
    public LocalDateTime retiredAt;
}
