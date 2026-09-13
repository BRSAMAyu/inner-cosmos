package com.innercosmos.ai.gateway;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CP-17: terminal bookkeeping for one provider SSE stream. The call manifest used to
 * stop at STREAM_OPENED — a stream that died mid-flight was indistinguishable from a
 * healthy one. This helper appends exactly one terminal outcome per stream:
 * STREAM_COMPLETED / STREAM_FAILED:&lt;ExceptionSimpleName&gt; / STREAM_TIMEOUT.
 *
 * First terminal wins: the servlet container also runs onCompletion after error/timeout
 * completion, so every callback is guarded by a compareAndSet and a dead stream can
 * never produce a contradictory "completed" record.
 */
public final class StreamOutcomeLedger {

    private final GatewayCallLedger ledger;
    private final Long userId;
    private final String moduleName;
    private final String provider;
    private final AtomicBoolean settled = new AtomicBoolean();

    private StreamOutcomeLedger(GatewayCallLedger ledger, Long userId, String moduleName, String provider) {
        this.ledger = ledger;
        this.userId = userId;
        this.moduleName = moduleName;
        this.provider = provider;
    }

    /** Records STREAM_OPENED and returns the stream's terminal ledger. */
    public static StreamOutcomeLedger open(GatewayCallLedger ledger, Long userId,
                                           String moduleName, String provider) {
        ledger.record(userId, moduleName, provider, "STREAM_OPENED");
        return new StreamOutcomeLedger(ledger, userId, moduleName, provider);
    }

    /** The stream ended normally. No-op if a terminal outcome was already recorded. */
    public void completed() {
        if (settled.compareAndSet(false, true)) {
            record("STREAM_COMPLETED");
        }
    }

    /** The stream broke mid-flight. No-op if a terminal outcome was already recorded. */
    public void failed(Throwable failure) {
        if (settled.compareAndSet(false, true)) {
            record("STREAM_FAILED:" + (failure == null ? "Unknown" : failure.getClass().getSimpleName()));
        }
    }

    /** The stream hit the async timeout. No-op if a terminal outcome was already recorded. */
    public void timedOut() {
        if (settled.compareAndSet(false, true)) {
            record("STREAM_TIMEOUT");
        }
    }

    public boolean settled() {
        return settled.get();
    }

    private void record(String outcome) {
        try {
            ledger.record(userId, moduleName, provider, outcome);
        } catch (RuntimeException ignored) {
            // Terminal bookkeeping is best-effort: a ledger failure must never surface
            // as a second, contradictory failure of the stream itself.
        }
    }
}
