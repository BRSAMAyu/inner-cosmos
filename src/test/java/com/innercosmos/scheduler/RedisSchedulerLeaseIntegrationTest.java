package com.innercosmos.scheduler;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import com.innercosmos.config.SchedulerLeaseConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RedisSchedulerLeaseIntegrationTest {
    private static final String IMAGE = "redis:7.4.2-alpine@sha256:"
            + "02419de7eddf55aa5bcf49efb74e88fa8d931b4d77c07eff8a6b2144472b6952";
    private static final String PASSWORD = "redis-scheduler-lease-contract-only";
    private static final String NAMESPACE = "inner-cosmos-scheduler-test-" + UUID.randomUUID();

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
    void twoPodsCannotExecuteTheSameSideEffectLeaseConcurrently() {
        LockProvider firstPod = new RedisLockProvider(connectionFactory, NAMESPACE);
        LockProvider secondPod = new RedisLockProvider(connectionFactory, NAMESPACE);
        LockConfiguration lease = lease("nightly-memory-settlement", Duration.ofSeconds(5));

        Optional<SimpleLock> first = firstPod.lock(lease);
        assertThat(first).isPresent();
        assertThat(secondPod.lock(lease("nightly-memory-settlement", Duration.ofSeconds(5)))).isEmpty();

        Set<String> keys = redis.keys("*nightly-memory-settlement*");
        assertThat(keys).hasSize(1);
        assertThat(redis.getExpire(keys.iterator().next())).isBetween(1L, 5L);

        first.orElseThrow().unlock();
        Optional<SimpleLock> recovered = secondPod.lock(
                lease("nightly-memory-settlement", Duration.ofSeconds(5)));
        assertThat(recovered).isPresent();
        recovered.orElseThrow().unlock();
    }

    @Test
    void abandonedLeaseExpiresSoAnotherPodCanRecover() throws Exception {
        LockProvider failedPod = new RedisLockProvider(connectionFactory, NAMESPACE);
        LockProvider recoveryPod = new RedisLockProvider(connectionFactory, NAMESPACE);

        assertThat(failedPod.lock(lease("crash-recovery", Duration.ofMillis(350)))).isPresent();
        assertThat(recoveryPod.lock(lease("crash-recovery", Duration.ofSeconds(2)))).isEmpty();

        Thread.sleep(500);

        Optional<SimpleLock> recovered = recoveryPod.lock(lease("crash-recovery", Duration.ofSeconds(2)));
        assertThat(recovered).isPresent();
        recovered.orElseThrow().unlock();
    }

    @Test
    void springAdvisorActuallySkipsAConcurrentAnnotatedInvocation() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            TestPropertyValues.of(
                    "inner-cosmos.scheduler.redis-lock.enabled=true",
                    "inner-cosmos.scheduler.redis-lock.namespace=" + NAMESPACE)
                    .applyTo(context);
            Probe probe = new Probe();
            context.registerBean(Probe.class, () -> probe);
            context.registerBean(RedisConnectionFactory.class,
                    RedisSchedulerLeaseIntegrationTest::newConnectionFactory,
                    definition -> definition.setDestroyMethodName("destroy"));
            context.register(SchedulerLeaseConfiguration.class, ContendedJob.class);
            context.refresh();

            ContendedJob job = context.getBean(ContendedJob.class);
            Thread first = new Thread(job::run, "first-scheduler-pod");
            first.start();
            try {
                assertThat(probe.entered.await(2, TimeUnit.SECONDS)).isTrue();
                job.run();
                assertThat(probe.invocations).hasValue(1);
            } finally {
                probe.release.countDown();
                first.join(2_000);
            }
            assertThat(first.isAlive()).isFalse();
        }
    }

    private static LockConfiguration lease(String name, Duration atMost) {
        return new LockConfiguration(Instant.now(), name, atMost, Duration.ZERO);
    }

    private static LettuceConnectionFactory newConnectionFactory() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        configuration.setPassword(RedisPassword.of(PASSWORD));
        LettuceConnectionFactory factory = new LettuceConnectionFactory(configuration);
        factory.afterPropertiesSet();
        factory.start();
        return factory;
    }

    static class Probe {
        private final AtomicInteger invocations = new AtomicInteger();
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
    }

    static class ContendedJob {
        private final Probe probe;

        ContendedJob(Probe probe) {
            this.probe = probe;
        }

        @SchedulerLock(name = "advisor-contract", lockAtMostFor = "PT10S")
        public void run() {
            probe.invocations.incrementAndGet();
            probe.entered.countDown();
            try {
                probe.release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
