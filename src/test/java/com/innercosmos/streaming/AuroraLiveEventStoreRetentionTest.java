package com.innercosmos.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * G2-SSE-CROSS-POD-REDIS-STREAM — deterministic cursor + bounded-retention evidence for the
 * process-local live store (the dev/test fan-out backend and the semantic contract the Redis
 * Stream backend mirrors under {@code XADD MAXLEN}). The cross-instance Redis proof lives in
 * {@link RedisAuroraStreamingStoreIntegrationTest} and is Docker-gated; this suite pins the same
 * cursor/retention/isolation guarantees without Docker so they run on every build.
 */
class AuroraLiveEventStoreRetentionTest {

    private static AuroraLiveEvent event(long userId, long turnId, long sequence) {
        return new AuroraLiveEvent(userId, turnId, sequence, turnId + ":" + sequence,
                "token", "{\"content\":\"c" + sequence + "\"}", false);
    }

    @Test
    void resumeCursorReturnsOnlyStrictlyLaterEventsWithoutDuplication() {
        InMemoryAuroraLiveEventStore store = new InMemoryAuroraLiveEventStore(1024);
        for (long seq = 0; seq <= 5; seq++) {
            store.publish(event(7L, 91L, seq));
        }

        // A client that has already consumed through sequence 2 must resume at 3, 4, 5 with no replay.
        assertThat(store.readAfter(7L, 91L, 2L, Duration.ZERO))
                .extracting(AuroraLiveEvent::sequence)
                .containsExactly(3L, 4L, 5L);
        // A caller already at the head sees nothing new (non-blocking probe returns empty, not a dup).
        assertThat(store.readAfter(7L, 91L, 5L, Duration.ZERO)).isEmpty();
        // The sequence-0 turn.started is reachable via the seq-inclusive existence probe.
        assertThat(store.readAfter(7L, 91L, -1L, Duration.ZERO))
                .extracting(AuroraLiveEvent::sequence)
                .containsExactly(0L, 1L, 2L, 3L, 4L, 5L);
    }

    @Test
    void liveStreamIsOwnerScopedAcrossTurns() {
        InMemoryAuroraLiveEventStore store = new InMemoryAuroraLiveEventStore(1024);
        store.publish(event(7L, 91L, 0L));
        store.publish(event(7L, 91L, 1L));

        assertThat(store.readAfter(8L, 91L, -1L, Duration.ZERO)).isEmpty();
        assertThat(store.readAfter(7L, 91L, -1L, Duration.ZERO)).hasSize(2);
    }

    @Test
    void boundedRetentionKeepsOnlyTheMostRecentWindow() {
        InMemoryAuroraLiveEventStore store = new InMemoryAuroraLiveEventStore(3);
        for (long seq = 0; seq <= 9; seq++) {
            store.publish(event(7L, 91L, seq));
        }

        // MAXLEN-style trimming retains the newest window; an old cursor still gets the live suffix
        // rather than an unbounded backlog. This bounds Redis Stream memory under sustained load.
        List<AuroraLiveEvent> retained = store.readAfter(7L, 91L, -1L, Duration.ZERO);
        assertThat(retained).extracting(AuroraLiveEvent::sequence).containsExactly(7L, 8L, 9L);
        assertThat(store.readAfter(7L, 91L, 8L, Duration.ZERO))
                .extracting(AuroraLiveEvent::sequence)
                .containsExactly(9L);
    }
}
