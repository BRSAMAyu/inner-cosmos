package com.innercosmos.payments.channel;

import java.util.Optional;
import java.util.Set;

/**
 * CP-45 channel status → ledger event mapping, exhaustive and closed: every raw status we
 * are willing to translate into a payment FACT is enumerated here per provider, and
 * anything else (non-terminal progress states, channel closures, unknown future statuses)
 * maps to empty — the ingest service then quarantines the callback for a human 查单 instead
 * of inventing a ledger row. Per the blueprint recovery rule: 不确定支付先查单，禁止盲重扣.
 */
public final class ChannelEventType {

    /** Raw statuses that mean money moved TO us (full or final capture). */
    private static final Set<String> WECHAT_PAYMENT = Set.of("TRANSACTION.SUCCESS");
    private static final Set<String> ALIPAY_PAYMENT = Set.of("TRADE_SUCCESS", "TRADE_FINISHED");
    /** Raw statuses that mean money moved BACK to the payer. */
    private static final Set<String> WECHAT_REFUND = Set.of("REFUND.SUCCESS");
    private static final Set<String> ALIPAY_REFUND = Set.of("REFUND_SUCCESS");

    private ChannelEventType() {
    }

    public static Optional<String> ledgerEventType(String provider, String rawStatus) {
        if (rawStatus == null) {
            return Optional.empty();
        }
        return switch (provider == null ? "" : provider) {
            case "wechatpay" -> map(rawStatus, WECHAT_PAYMENT, WECHAT_REFUND);
            case "alipay" -> map(rawStatus, ALIPAY_PAYMENT, ALIPAY_REFUND);
            default -> Optional.empty();
        };
    }

    private static Optional<String> map(String rawStatus, Set<String> payment, Set<String> refund) {
        if (payment.contains(rawStatus)) return Optional.of("PAYMENT_SUCCEEDED");
        if (refund.contains(rawStatus)) return Optional.of("REFUND_SUCCEEDED");
        return Optional.empty();
    }
}
