package com.innercosmos.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/** Fails production startup before demo data or traffic can use unsafe defaults. */
@Component
@Profile("prod")
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class ProductionStartupGuard implements ApplicationRunner {

    private static final Set<String> REAL_PROVIDERS =
            Set.of("glm", "mimo", "minimax", "deepseek", "openai-compatible");
    private static final Set<String> RUNTIME_ROLES =
            Set.of("all", "api", "worker", "scheduler", "migration");

    private final Environment environment;

    public ProductionStartupGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        validate();
    }

    void validate() {
        String role = environment.getProperty("inner-cosmos.runtime.role", "all").toLowerCase(Locale.ROOT);
        if (!RUNTIME_ROLES.contains(role)) {
            throw unsafe("runtime role is invalid");
        }
        requireEquals("llm.mode", "prod", "production LLM mode");
        requireFalse("llm.allow-fallback", "Mock fallback");
        requireFalse("inner-cosmos.demo.seed-enabled", "demo data seeding");

        if (role.equals("all") || role.equals("api")) {
            validateApiSecurity();
        }
        if (role.equals("all") || role.equals("api") || role.equals("worker") || role.equals("scheduler")) {
            validateLlm();
        }
        if (role.equals("all") || role.equals("api") || role.equals("scheduler")) {
            validateRedisConnection();
        }
        if (role.equals("all") || role.equals("scheduler")) {
            requireTrue("inner-cosmos.scheduler.redis-lock.enabled", "Redis-backed scheduler leases");
            required("inner-cosmos.scheduler.redis-lock.namespace", "Redis scheduler lease namespace");
        }
        if (role.equals("api") || role.equals("worker")) {
            requireTrue("inner-cosmos.events.outbox.enabled", "JDBC outbox delivery");
        }

        validateDatabase(role);
    }

    private void validateApiSecurity() {
        requireTrue("server.servlet.session.cookie.secure", "secure session cookies");
        requireTrue("inner-cosmos.security.csrf-enabled", "CSRF protection");
        requireTrue("inner-cosmos.auth.oidc.enabled", "OIDC resource-server authentication");
        requireTrue("inner-cosmos.session.redis.enabled", "Redis-backed HTTP sessions");
        requireTrue("inner-cosmos.security.rate-limit.redis.enabled", "Redis-backed distributed rate limiting");
        requireTrue("inner-cosmos.idempotency.redis.enabled", "Redis-backed idempotency");
        required("spring.session.redis.namespace", "Redis session namespace");
        required("inner-cosmos.security.rate-limit.redis.namespace", "Redis rate-limit namespace");
        required("inner-cosmos.idempotency.redis.namespace", "Redis idempotency namespace");
        requireHttps("inner-cosmos.auth.oidc.issuer-uri", "OIDC issuer URI");
        requireHttps("inner-cosmos.auth.oidc.jwk-set-uri", "OIDC JWK set URI");
        required("inner-cosmos.auth.oidc.audience", "OIDC API audience");
        requireHttps("inner-cosmos.auth.oidc.authorization-uri", "OIDC authorization endpoint");
        requireHttps("inner-cosmos.auth.oidc.token-uri", "OIDC token endpoint");
        required("inner-cosmos.auth.oidc.client-id", "OIDC mobile public-client ID");
        required("inner-cosmos.auth.oidc.redirect-uri", "OIDC mobile redirect URI");
    }

    private void validateRedisConnection() {
        requireTrue("spring.data.redis.ssl.enabled", "Redis transport TLS");
        required("spring.data.redis.host", "Redis host");
        required("spring.data.redis.password", "Redis credential");
    }

    private void validateLlm() {
        String provider = required("llm.provider", "LLM provider").toLowerCase(Locale.ROOT);
        if (!REAL_PROVIDERS.contains(provider)) {
            throw unsafe("LLM provider must be a supported non-Mock provider");
        }
        String providerKey = environment.getProperty("llm." + provider + ".api-key", "");
        String topLevelKey = environment.getProperty("llm.api-key", "");
        if (providerKey.isBlank() && topLevelKey.isBlank()) {
            throw unsafe("active LLM provider credential is not configured");
        }
    }

    private void validateDatabase(String role) {
        String jdbcUrl = required("spring.datasource.url", "production datasource URL")
                .toLowerCase(Locale.ROOT);
        if (!jdbcUrl.startsWith("jdbc:postgresql:")) {
            throw unsafe("production datasource must use PostgreSQL");
        }
        if (!(jdbcUrl.contains("sslmode=verify-full") || jdbcUrl.contains("sslmode=verify-ca"))) {
            throw unsafe("production datasource TLS must verify the server certificate");
        }
        boolean flywayEnabled = environment.getProperty("spring.flyway.enabled", Boolean.class, false);
        if ((role.equals("all") || role.equals("migration")) && !flywayEnabled) {
            throw unsafe("Flyway must own production schema migration for this runtime role");
        }
        if (!(role.equals("all") || role.equals("migration")) && flywayEnabled) {
            throw unsafe("Flyway must be disabled outside the migration runtime role");
        }
        if (!"never".equalsIgnoreCase(environment.getProperty("spring.sql.init.mode", ""))) {
            throw unsafe("legacy SQL initialization must be disabled in production");
        }
        required("spring.datasource.username", "production datasource username");
        required("spring.datasource.password", "production datasource password");
    }

    private void requireEquals(String property, String expected, String description) {
        if (!expected.equalsIgnoreCase(environment.getProperty(property, ""))) {
            throw unsafe(description + " is invalid");
        }
    }

    private void requireFalse(String property, String description) {
        if (environment.getProperty(property, Boolean.class, true)) {
            throw unsafe(description + " must be disabled");
        }
    }

    private void requireTrue(String property, String description) {
        if (!environment.getProperty(property, Boolean.class, false)) {
            throw unsafe(description + " must be enabled");
        }
    }

    private String required(String property, String description) {
        String value = environment.getProperty(property, "");
        if (value.isBlank()) {
            throw unsafe(description + " is not configured");
        }
        return value;
    }

    private void requireHttps(String property, String description) {
        if (!required(property, description).toLowerCase(Locale.ROOT).startsWith("https://")) {
            throw unsafe(description + " must use HTTPS");
        }
    }

    private IllegalStateException unsafe(String reason) {
        return new IllegalStateException("Production startup rejected: " + reason
                + ". No credential values were logged.");
    }
}
