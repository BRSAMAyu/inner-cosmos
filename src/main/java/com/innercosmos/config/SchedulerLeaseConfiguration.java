package com.innercosmos.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Cross-pod leases for scheduled side effects. Local development keeps ordinary
 * Spring scheduling; production is required to enable this Redis-backed advisor.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "inner-cosmos.scheduler.redis-lock.enabled", havingValue = "true")
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class SchedulerLeaseConfiguration {

    @Bean
    LockProvider schedulerLockProvider(
            RedisConnectionFactory connectionFactory,
            @Value("${inner-cosmos.scheduler.redis-lock.namespace:inner-cosmos-scheduler-v1}")
            String namespace) {
        return new RedisLockProvider(connectionFactory, namespace);
    }
}
