package com.innercosmos.payments.channel;

import java.util.Map;

/**
 * CP-45 channel adapter contract: translate ONE provider's callback wire format into the
 * canonical {@link ChannelCallback} so the single CP-47 verification + ledger kernel can
 * serve every channel. Adapters are format codecs only — they never decide whether an
 * event is trustworthy (verification) or what it means for entitlements (status mapping
 * lives in {@link ChannelEventType}); both stay server-side and fail-closed.
 *
 * <p>Sandbox honesty note: in sandbox mode the canonical triple is signed with the
 * operator-injected HMAC secret via the CP-47 verifier. Production channel verification
 * additionally requires the channel's own asymmetric scheme (WeChat Pay platform
 * certificate RSA-SHA256 over {@code timestamp\nnonce\nbody\n}; Alipay RSA2 over the
 * sorted parameter string) with operator-provided certificates/keys — a human gate that
 * cannot and must not be simulated in code.
 */
public interface ChannelCallbackAdapter {

    /** Provider slug used in the callback URL and ledger rows, e.g. "wechatpay". */
    String provider();

    /**
     * Parses the raw body + headers into the canonical form. Header lookup is
     * case-insensitive (servlet containers normalize inconsistently). Throws
     * {@link ChannelDecodeException} on anything malformed — never returns a half-decoded
     * callback.
     */
    ChannelCallback decode(String rawBody, Map<String, String> headers);

    /** The literal body the channel expects as "received, stop retrying" (or its failure twin). */
    String ackBody(boolean accepted);
}
