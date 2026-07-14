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

    private final Environment environment;

    public ProductionStartupGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        validate();
    }

    void validate() {
        requireEquals("llm.mode", "prod", "production LLM mode");
        requireFalse("llm.allow-fallback", "Mock fallback");
        requireFalse("inner-cosmos.demo.seed-enabled", "demo data seeding");
        requireTrue("server.servlet.session.cookie.secure", "secure session cookies");

        String provider = required("llm.provider", "LLM provider").toLowerCase(Locale.ROOT);
        if (!REAL_PROVIDERS.contains(provider)) {
            throw unsafe("LLM provider must be a supported non-Mock provider");
        }
        String providerKey = environment.getProperty("llm." + provider + ".api-key", "");
        String topLevelKey = environment.getProperty("llm.api-key", "");
        if (providerKey.isBlank() && topLevelKey.isBlank()) {
            throw unsafe("active LLM provider credential is not configured");
        }

        String jdbcUrl = required("spring.datasource.url", "production datasource URL")
                .toLowerCase(Locale.ROOT);
        if (!jdbcUrl.startsWith("jdbc:mysql:")) {
            throw unsafe("production datasource must use MySQL");
        }
        if (!(jdbcUrl.contains("sslmode=verify_identity") || jdbcUrl.contains("sslmode=verify_ca"))) {
            throw unsafe("production datasource TLS must verify the server certificate");
        }
        if (jdbcUrl.contains("allowpublickeyretrieval=true")) {
            throw unsafe("production datasource must not allow public key retrieval");
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

    private IllegalStateException unsafe(String reason) {
        return new IllegalStateException("Production startup rejected: " + reason
                + ". No credential values were logged.");
    }
}
