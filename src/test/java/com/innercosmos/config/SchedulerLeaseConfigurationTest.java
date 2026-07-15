package com.innercosmos.config;

import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SchedulerLeaseConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulerLeaseConfiguration.class)
            .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class));

    @Test
    void createsRedisLockProviderOnlyWhenExplicitlyEnabled() {
        runner.withPropertyValues(
                        "inner-cosmos.scheduler.redis-lock.enabled=true",
                        "inner-cosmos.scheduler.redis-lock.namespace=test-scheduler")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(LockProvider.class);
                });
    }

    @Test
    void localDevelopmentDoesNotSilentlyRequireRedis() {
        runner.run(context -> assertThat(context).doesNotHaveBean(LockProvider.class));
    }
}
