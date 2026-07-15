package com.innercosmos.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisRateLimitStoreFailureTest {
    @Test
    void redisFailureIsExplicitAndNeverSilentlyAllowsTraffic() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("contract failure"));

        RedisRateLimitStore store = new RedisRateLimitStore(redis, "test");
        assertThatThrownBy(() -> store.consume("user:hash", new RateLimitPolicy(1, 1, 1)))
                .isInstanceOf(RateLimitStoreUnavailableException.class);
    }
}
