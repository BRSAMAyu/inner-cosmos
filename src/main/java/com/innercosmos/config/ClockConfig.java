package com.innercosmos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Gemini audit 1.7 (PARTIAL/P1): business time sources must flow through a single injected
 * {@link Clock} rather than ad-hoc {@code Instant.now()}/{@code LocalDateTime.now()} calls
 * scattered across services, so production always advances on one explicit UTC instant source
 * and tests can pin/advance "now" deterministically. Any user-facing display or daily-quota
 * boundary calculation still applies the affected user's own IANA timezone on top of this
 * clock (see e.g. PersonaChatServiceImpl#resolveQuotaZone) -- this bean intentionally does NOT
 * hardcode any timezone; it is a zone-less instant source only.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
