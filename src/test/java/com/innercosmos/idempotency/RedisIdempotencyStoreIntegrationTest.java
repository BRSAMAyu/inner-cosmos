package com.innercosmos.idempotency;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
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

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RedisIdempotencyStoreIntegrationTest {
    private static final String IMAGE = "redis:7.4.2-alpine@sha256:"
            + "02419de7eddf55aa5bcf49efb74e88fa8d931b4d77c07eff8a6b2144472b6952";
    private static final String PASSWORD = "redis-idempotency-contract-only";
    private static final String NAMESPACE = "inner-cosmos:test:idempotency:" + UUID.randomUUID();

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse(IMAGE))
            .withExposedPorts(6379).withCommand("redis-server", "--requirepass", PASSWORD);

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
        if (connectionFactory != null) connectionFactory.destroy();
    }

    @Test
    void twoPodsShareAtomicClaimReplayAndPayloadConflict() {
        RedisIdempotencyStore firstPod = new RedisIdempotencyStore(redis, NAMESPACE);
        RedisIdempotencyStore secondPod = new RedisIdempotencyStore(redis, NAMESPACE);
        Duration ttl = Duration.ofMinutes(5);
        String key = "owner-route-key";

        assertThat(firstPod.claim(key, "fingerprint-a", ttl).state())
                .isEqualTo(IdempotencyClaim.State.ACQUIRED);
        assertThat(secondPod.claim(key, "fingerprint-a", ttl).state())
                .isEqualTo(IdempotencyClaim.State.IN_PROGRESS);
        firstPod.complete(key, "fingerprint-a",
                new CachedHttpResponse(201, "application/json", "{\"id\":7}".getBytes(StandardCharsets.UTF_8)), ttl);

        IdempotencyClaim replay = secondPod.claim(key, "fingerprint-a", ttl);
        assertThat(replay.state()).isEqualTo(IdempotencyClaim.State.REPLAY);
        assertThat(replay.response().status()).isEqualTo(201);
        assertThat(new String(replay.response().body(), StandardCharsets.UTF_8)).isEqualTo("{\"id\":7}");
        assertThat(firstPod.claim(key, "fingerprint-b", ttl).state())
                .isEqualTo(IdempotencyClaim.State.CONFLICT);
        assertThat(redis.getExpire(NAMESPACE + ":" + key)).isPositive();
    }
}
