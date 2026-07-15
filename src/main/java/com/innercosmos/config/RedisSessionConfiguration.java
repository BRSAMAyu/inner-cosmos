package com.innercosmos.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * Externalizes only short-lived browser session state. PostgreSQL remains the
 * source of truth for users, memories, Aurora state and every confirmed domain fact.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "inner-cosmos.session.redis.enabled", havingValue = "true")
@EnableRedisHttpSession(
        maxInactiveIntervalInSeconds = 1800,
        redisNamespace = "${spring.session.redis.namespace:inner-cosmos:session}")
public class RedisSessionConfiguration {
}
