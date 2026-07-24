package com.innercosmos.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

import java.util.Locale;
import java.util.Set;

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
    private static final Set<String> SAME_SITE_VALUES = Set.of("lax", "strict", "none");

    /**
     * Spring Session owns the {@code SESSION} cookie when Redis is enabled. Servlet-container
     * cookie properties alone do not customize that serializer consistently, so the public demo
     * was still emitting {@code SameSite=Lax} even with {@code COOKIE_SAME_SITE=none}. Keep one
     * explicit contract for every Redis-backed environment.
     */
    @Bean
    CookieSerializer sessionCookieSerializer(Environment environment) {
        String configured = environment.getProperty(
                "server.servlet.session.cookie.same-site", "lax").toLowerCase(Locale.ROOT);
        if (!SAME_SITE_VALUES.contains(configured)) {
            throw new IllegalStateException("Unsupported session cookie SameSite policy");
        }
        String sameSite = Character.toUpperCase(configured.charAt(0)) + configured.substring(1);
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SESSION");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(environment.getProperty(
                "server.servlet.session.cookie.secure", Boolean.class, false));
        serializer.setSameSite(sameSite);
        return serializer;
    }
}
