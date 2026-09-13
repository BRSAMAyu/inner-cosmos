package com.innercosmos.payments.channel;

/**
 * CP-45: a callback body/header set that does not even parse into the channel's own
 * documented shape. Malformed input is quarantined for inspection — it is never recorded
 * into the payment ledger, and the channel is told to retry (standard async-notify
 * semantics) so the fact is not silently lost.
 */
public class ChannelDecodeException extends RuntimeException {
    public ChannelDecodeException(String message) {
        super(message);
    }
}
