package com.innercosmos.streaming;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Gemini audit 2.7 (CONFIRMED/P1): structural proof (independent of timing) that {@code publish()}
 * no longer performs the vulnerable two-step "XADD, then a SEPARATE expire()" sequence at all.
 * Before this fix, a crash/restart landing between those two Redis round trips could leave a
 * stream key with an appended entry and NO TTL (unbounded retention) -- that vulnerability is a
 * structural property of making two independent calls, not something a timing-based test can
 * reliably reproduce (see RedisAuroraLiveEventStoreTtlAtomicityTest for the real-Redis TTL/
 * concurrency behavior this enables). This test instead pins the STRUCTURE: publish() must issue
 * exactly one redis.execute(script, ...) call and must never touch redis.opsForStream().add(...)
 * or redis.expire(...) -- the two-call surface the old vulnerable code used -- at all.
 */
@ExtendWith(MockitoExtension.class)
class RedisAuroraLiveEventStorePublishAtomicityUnitTest {

    @Mock
    private StringRedisTemplate redis;

    private AuroraLiveEvent event() {
        return new AuroraLiveEvent(1L, 100L, 0L, "100:0", "token", "{\"content\":\"c\"}", false);
    }

    @Test
    @DisplayName("2.7: publish() issues exactly ONE redis.execute(script, ...) call -- never a separate opsForStream().add() + expire() pair")
    void publish_isASingleAtomicScriptCall_notTwoSeparateRedisCommands() {
        RedisAuroraLiveEventStore store = new RedisAuroraLiveEventStore(
                redis, "test:aurora:atomic-unit", Duration.ofMinutes(2), 128);

        store.publish(event());

        // Exactly one atomic script execution -- this IS the fix: XADD (with MAXLEN trim) and
        // EXPIRE both happen inside this single server-side atomic unit.
        verify(redis, times(1)).execute(any(RedisScript.class), anyList(), any(Object[].class));
        // The old vulnerable two-call surface must never be touched at all by publish() anymore.
        verify(redis, never()).opsForStream();
        verify(redis, never()).expire(anyString(), any(Duration.class));
    }
}
