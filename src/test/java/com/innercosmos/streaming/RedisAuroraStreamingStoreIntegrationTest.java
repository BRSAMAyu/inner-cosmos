package com.innercosmos.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.dto.ChatRequest;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class RedisAuroraStreamingStoreIntegrationTest {
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

    @Test
    void stagedPrivateContextIsOwnerBoundOneUseAndCrossInstance() {
        RedisAuroraStreamStageStore first = new RedisAuroraStreamStageStore(
                redis, new ObjectMapper(), "test:aurora:stage", Duration.ofMinutes(1));
        RedisAuroraStreamStageStore second = new RedisAuroraStreamStageStore(
                redis, new ObjectMapper(), "test:aurora:stage", Duration.ofMinutes(1));
        ChatRequest request = new ChatRequest();
        request.sessionId = 41L;
        request.message = "private cross pod text";
        request.mode = "DAILY_TALK";

        String token = first.stage(7L, request);

        assertThat(second.consume(8L, token)).isNull();
        ChatRequest consumed = second.consume(7L, token);
        assertThat(consumed).isNotNull();
        assertThat(consumed.sessionId).isEqualTo(41L);
        assertThat(consumed.message).isEqualTo("private cross pod text");
        assertThat(first.consume(7L, token)).isNull();
    }

    @Test
    void liveEventsReplayAndBlockAcrossIndependentInstances() throws Exception {
        RedisAuroraLiveEventStore producer = new RedisAuroraLiveEventStore(
                redis, "test:aurora:live", Duration.ofMinutes(2), 128);
        RedisAuroraLiveEventStore follower = new RedisAuroraLiveEventStore(
                redis, "test:aurora:live", Duration.ofMinutes(2), 128);
        producer.publish(new AuroraLiveEvent(7L, 91L, 1L, "91:1", "token",
                "{\"content\":\"hello\"}", false));

        assertThat(follower.readAfter(7L, 91L, 0L, Duration.ZERO))
                .extracting(AuroraLiveEvent::name)
                .containsExactly("token");
        assertThat(follower.readAfter(8L, 91L, 0L, Duration.ZERO)).isEmpty();

        CompletableFuture<List<AuroraLiveEvent>> waiting = CompletableFuture.supplyAsync(
                () -> follower.readAfter(7L, 91L, 1L, Duration.ofSeconds(3)));
        Thread.sleep(150L);
        producer.publish(new AuroraLiveEvent(7L, 91L, 2L, "91:2", "turn.completed",
                "{\"message\":\"done\"}", true));

        List<AuroraLiveEvent> resumed = waiting.get(5, TimeUnit.SECONDS);
        assertThat(resumed).hasSize(1);
        assertThat(resumed.getFirst().sequence()).isEqualTo(2L);
        assertThat(resumed.getFirst().terminal()).isTrue();
    }
}
