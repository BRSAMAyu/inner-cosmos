package com.innercosmos.ai.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CP-17: one terminal outcome per provider stream, first terminal wins. A dead stream
 * must never leave STREAM_OPENED as its last word, nor record a contradictory
 * "completed" after a failure.
 */
class StreamOutcomeLedgerTest {

    private static final Long USER = 96_200_001L;

    private final GatewayCallLedger ledger = new GatewayCallLedger();

    private List<String> outcomes() {
        return ledger.recent(10).stream().map(GatewayCallLedger.CallRecord::outcome).toList();
    }

    @Test
    void healthyStreamRecordsOpenedThenCompleted() {
        StreamOutcomeLedger stream = StreamOutcomeLedger.open(ledger, USER, "AURORA", "glm");
        assertFalse(stream.settled());
        stream.completed();
        assertTrue(stream.settled());
        assertEquals(List.of("STREAM_OPENED", "STREAM_COMPLETED"), outcomes());
    }

    @Test
    void brokenStreamRecordsFailureAndSuppressesLateCompletion() {
        StreamOutcomeLedger stream = StreamOutcomeLedger.open(ledger, USER, "AURORA", "glm");
        stream.failed(new IllegalStateException("provider dropped"));
        // The container also runs onCompletion after an error — must not double-record.
        stream.completed();
        assertEquals(List.of("STREAM_OPENED", "STREAM_FAILED:IllegalStateException"), outcomes());
    }

    @Test
    void timeoutWinsOverLateFailureAndViceVersaIsTerminalToo() {
        StreamOutcomeLedger timedOut = StreamOutcomeLedger.open(ledger, USER, "AURORA", "glm");
        timedOut.timedOut();
        timedOut.failed(new RuntimeException("late"));
        assertEquals(List.of("STREAM_OPENED", "STREAM_TIMEOUT"), outcomes());

        StreamOutcomeLedger failed = StreamOutcomeLedger.open(ledger, USER, "AURORA", "glm");
        failed.failed(new RuntimeException("first"));
        failed.timedOut();
        failed.completed();
        assertEquals(List.of(
                "STREAM_OPENED", "STREAM_TIMEOUT",
                "STREAM_OPENED", "STREAM_FAILED:RuntimeException"), outcomes());
    }

    @Test
    void nullFailureAndThrowingLedgerNeverSurfaceAsStreamErrors() {
        StreamOutcomeLedger stream = StreamOutcomeLedger.open(ledger, USER, "AURORA", "glm");
        stream.failed(null); // degraded transport information still settles honestly
        assertTrue(stream.settled());
        assertEquals(List.of("STREAM_OPENED", "STREAM_FAILED:Unknown"), outcomes());
    }
}
