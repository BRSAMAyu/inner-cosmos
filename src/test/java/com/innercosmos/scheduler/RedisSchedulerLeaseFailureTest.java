package com.innercosmos.scheduler;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisSchedulerLeaseFailureTest {

    @Test
    void unavailableRedisNeverFailsOpenIntoDuplicateExecution() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        when(connectionFactory.getConnection())
                .thenThrow(new RedisConnectionFailureException("test Redis unavailable"));
        RedisLockProvider provider = new RedisLockProvider(connectionFactory, "test-scheduler");
        LockConfiguration lease = new LockConfiguration(
                Instant.now(), "side-effect", Duration.ofMinutes(1), Duration.ZERO);

        assertThatThrownBy(() -> provider.lock(lease))
                .isInstanceOf(RedisConnectionFailureException.class);
    }
}
