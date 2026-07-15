package com.innercosmos.config;

import com.innercosmos.ratelimit.RateLimitDecision;
import com.innercosmos.ratelimit.RateLimitStore;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestRateLimitConfig {

    @Bean
    @Primary
    public RateLimitStore rateLimitStore() {
        return (key, policy) -> new RateLimitDecision(true, policy.capacity());
    }
}
