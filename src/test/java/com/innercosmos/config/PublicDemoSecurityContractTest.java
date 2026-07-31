package com.innercosmos.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Prevents presentation convenience from silently disabling formal browser and abuse boundaries.
 * Capacity may be tuned for a classroom, but the controls themselves stay enabled.
 */
class PublicDemoSecurityContractTest {

    @Test
    void publicDemoKeepsCsrfAndDistributedRateLimitsEnabled() throws IOException {
        String compose = Files.readString(Path.of("deploy", "compose", "public-demo.yml"));

        assertThat(compose)
                .contains("REDIS_RATE_LIMIT_ENABLED: \"true\"")
                .contains("DEMO_UNLIMITED_USAGE_ENABLED: \"false\"")
                .contains("INNER_COSMOS_SECURITY_CSRF_ENABLED: \"true\"")
                .contains("MEMORY_EMBEDDING_ENABLED: ${MEMORY_EMBEDDING_ENABLED:-true}")
                .contains("MEMORY_EMBEDDING_API_KEY: ${MEMORY_EMBEDDING_API_KEY:")
                .doesNotContain("REDIS_RATE_LIMIT_ENABLED: \"false\"")
                .doesNotContain("INNER_COSMOS_SECURITY_CSRF_ENABLED: \"false\"")
                .doesNotContain("MEMORY_EMBEDDING_ENABLED: \"false\"");
    }

    @Test
    void localCompleteDoesNotAdvertiseUnlimitedDemoUsage() throws IOException {
        String compose = Files.readString(Path.of("deploy", "compose", "local-complete.yml"));

        assertThat(compose)
                .contains("REDIS_RATE_LIMIT_ENABLED: \"true\"")
                .contains("DEMO_UNLIMITED_USAGE_ENABLED: \"false\"");
    }
}
