package com.innercosmos.ratelimit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RedisRateLimitStoreIntegrationTest {
    private static final String IMAGE = "redis:7.4.2-alpine@sha256:"
            + "02419de7eddf55aa5bcf49efb74e88fa8d931b4d77c07eff8a6b2144472b6952";
    private static final String PASSWORD = "redis-rate-limit-contract-only";
    private static final String NAMESPACE = "inner-cosmos:test:rate-limit:" + UUID.randomUUID();

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse(IMAGE))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--requirepass", PASSWORD);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;

    @BeforeAll
    static void connect() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        configuration.setPassword(RedisPassword.of(PASSWORD));
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
    }

    @AfterAll
    static void disconnect() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void twoApplicationInstancesConsumeOneAtomicSharedBucket() {
        RedisRateLimitStore firstPod = new RedisRateLimitStore(redis, NAMESPACE);
        RedisRateLimitStore secondPod = new RedisRateLimitStore(redis, NAMESPACE);
        RateLimitPolicy policy = new RateLimitPolicy(2, 1, 2);
        String key = RateLimitKey.forSubject("user", "42");

        assertThat(firstPod.consume(key, policy)).isEqualTo(new RateLimitDecision(true, 1));
        assertThat(secondPod.consume(key, policy)).isEqualTo(new RateLimitDecision(true, 0));
        assertThat(firstPod.consume(key, policy)).isEqualTo(new RateLimitDecision(false, 0));

        Set<String> keys = redis.keys(NAMESPACE + ":user:*");
        assertThat(keys).hasSize(1);
        assertThat(keys.iterator().next()).isEqualTo(NAMESPACE + ":" + key);
        assertThat(redis.getExpire(keys.iterator().next())).isBetween(1L, 120L);
    }

    @Test
    void identitiesDoNotShareBuckets() {
        RedisRateLimitStore store = new RedisRateLimitStore(redis, NAMESPACE);
        RateLimitPolicy policy = new RateLimitPolicy(1, 1, 1);

        assertThat(store.consume(RateLimitKey.forSubject("login", "192.0.2.10"), policy).allowed()).isTrue();
        assertThat(store.consume(RateLimitKey.forSubject("login", "192.0.2.11"), policy).allowed()).isTrue();
        assertThat(store.consume(RateLimitKey.forSubject("login", "192.0.2.10"), policy).allowed()).isFalse();
    }
}
