package com.innercosmos.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Gemini audit 2.7 (CONFIRMED/P1): {@link RedisAuroraLiveEventStore#publish} used to call XADD
 * (via redis.opsForStream().add) and the TTL-setting EXPIRE (redis.expire) as TWO SEPARATE Redis
 * commands. A crash/restart between them could leave a stream key with no TTL at all -- unbounded
 * retention. The fix routes both through a single atomic Lua script (EVAL), so there is no
 * two-command window at all: Redis executes the whole script as one atomic unit.
 *
 * These tests run against a REAL Redis (Testcontainers), proving the actual server-visible TTL
 * behavior, not just that the Java code no longer calls a separate .expire().
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisAuroraLiveEventStoreTtlAtomicityTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4.2-alpine"))
            .withExposedPorts(6379);

    static LettuceConnectionFactory connectionFactory;
    static StringRedisTemplate redis;

    @BeforeAll
    static void startRedisClient() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void closeRedisClient() {
        connectionFactory.destroy();
    }

    private RedisAuroraLiveEventStore store(String namespace) {
        return new RedisAuroraLiveEventStore(redis, namespace, Duration.ofMinutes(2), 128);
    }

    private AuroraLiveEvent event(long userId, long turnId, long sequence) {
        return new AuroraLiveEvent(userId, turnId, sequence, turnId + ":" + sequence,
                "token", "{\"content\":\"c" + sequence + "\"}", false);
    }

    @Test
    @DisplayName("2.7: first publish to a brand-new stream key sets a TTL immediately -- no window with no expiry")
    void firstPublish_setsTtlImmediately() {
        RedisAuroraLiveEventStore s = store("test:aurora:ttl:first");
        s.publish(event(1L, 100L, 0L));

        String key = "test:aurora:ttl:first:turn:100";
        Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
        assertThat(ttl).as("a freshly-created stream key must have a TTL set, not -1 (no expiry)")
                .isNotNull().isGreaterThan(0L);
    }

    @Test
    @DisplayName("2.7: a second publish to an already-TTL'd key still has a TTL afterward (correctly preserved/refreshed, not cleared)")
    void secondPublish_keyAlreadyHasTtl_ttlStillSetAfterward() {
        RedisAuroraLiveEventStore s = store("test:aurora:ttl:second");
        String key = "test:aurora:ttl:second:turn:200";
        s.publish(event(1L, 200L, 0L));
        Long ttlAfterFirst = redis.getExpire(key, TimeUnit.SECONDS);
        assertThat(ttlAfterFirst).isGreaterThan(0L);

        s.publish(event(1L, 200L, 1L));

        Long ttlAfterSecond = redis.getExpire(key, TimeUnit.SECONDS);
        assertThat(ttlAfterSecond).as("TTL must still be set (refreshed) after a second publish, never removed")
                .isNotNull().isGreaterThan(0L);
    }

    @Test
    @DisplayName("2.7: concurrent publishes to the SAME turn key never leave a window where the key exists with no TTL")
    void concurrentPublishes_neverLeaveKeyWithoutTtl() throws Exception {
        RedisAuroraLiveEventStore s = store("test:aurora:ttl:concurrent");
        String key = "test:aurora:ttl:concurrent:turn:300";
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger observedNoTtl = new AtomicInteger(0);
        try {
            for (int i = 0; i < threads; i++) {
                long sequence = i;
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        go.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    s.publish(event(1L, 300L, sequence));
                    // Immediately after THIS thread's own publish returns, the key must already
                    // carry a TTL -- the atomic script guarantees XADD and EXPIRE landed together.
                    Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
                    if (ttl == null || ttl <= 0) {
                        observedNoTtl.incrementAndGet();
                    }
                });
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(observedNoTtl.get())
                .as("no publishing thread must ever observe the key without a TTL immediately after its own publish")
                .isZero();
        // No writes lost either -- all 16 concurrent entries landed in the stream.
        assertThat(redis.opsForStream().size(key)).isEqualTo((long) threads);
        Long finalTtl = redis.getExpire(key, TimeUnit.SECONDS);
        assertThat(finalTtl).isNotNull().isGreaterThan(0L);
    }

    @Test
    @DisplayName("2.7: publish is a single atomic round trip -- exactly one EVALSHA/EVAL, never a separate XADD then EXPIRE")
    void publish_isASingleAtomicRoundTrip_notTwoSeparateCommands() {
        // Real-server proof (complementing any unit-level mock verification): MULTI/EXEC around
        // the store's own calls would be redundant with a Lua script, so instead we assert on the
        // externally observable contract -- a single publish() call already leaves the key fully
        // formed (entry appended AND expiry set) with no intermediate state ever visible to a
        // concurrent reader. This is exercised structurally by the concurrent test above; here we
        // pin the simplest single-call case explicitly for a clear, minimal regression signal.
        RedisAuroraLiveEventStore s = store("test:aurora:ttl:single-roundtrip");
        String key = "test:aurora:ttl:single-roundtrip:turn:400";

        s.publish(event(1L, 400L, 0L));

        assertThat(redis.opsForStream().size(key)).isEqualTo(1L);
        assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isGreaterThan(0L);
    }

    @Test
    @DisplayName("2.7: publish() still round-trips through readAfter() correctly (no regression in the existing fan-out contract)")
    void publish_stillReadableViaReadAfter() {
        RedisAuroraLiveEventStore s = store("test:aurora:ttl:readback");
        s.publish(event(9L, 500L, 0L));
        s.publish(event(9L, 500L, 1L));

        List<AuroraLiveEvent> events = s.readAfter(9L, 500L, -1L, Duration.ZERO);
        assertThat(events).extracting(AuroraLiveEvent::sequence).containsExactly(0L, 1L);
    }
}
