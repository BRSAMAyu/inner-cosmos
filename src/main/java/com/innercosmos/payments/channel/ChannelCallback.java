package com.innercosmos.payments.channel;

import java.time.LocalDateTime;

/**
 * CP-45 canonical decoded form of one channel callback, provider-neutral after the adapter
 * ran. The (timestamp, canonicalBody, signatureHex) triple is what the CP-47
 * {@link com.innercosmos.payments.PaymentCallbackVerifier} binds; amountCents is always
 * CNY minor units regardless of how the channel encodes it (WeChat fen natively, Alipay
 * yuan decimal); merchantId carries the channel account identity the ingest service
 * validates against operator configuration — fail-closed, never trusted from the payload
 * alone. No payer personal data survives decoding.
 */
public record ChannelCallback(
        String provider,
        String providerEventId,
        String orderId,
        String rawStatus,
        String merchantId,
        String currency,
        long amountCents,
        LocalDateTime occurredAt,
        String timestamp,
        String canonicalBody,
        String signatureHex) {
}
