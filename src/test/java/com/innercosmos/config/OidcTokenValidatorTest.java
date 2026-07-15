package com.innercosmos.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OidcTokenValidatorTest {
    private final OidcProperties properties = properties();

    @Test
    void acceptsCurrentTokenWithExactIssuerAndAudience() {
        assertThat(OidcSecurityConfiguration.tokenValidator(properties)
                .validate(jwt("https://identity.example/", List.of("other", "inner-cosmos-api"), 300))
                .hasErrors()).isFalse();
    }

    @Test
    void rejectsWrongIssuerWrongAudienceAndExpiredToken() {
        assertThat(OidcSecurityConfiguration.tokenValidator(properties)
                .validate(jwt("https://attacker.example/", List.of("inner-cosmos-api"), 300))
                .hasErrors()).isTrue();
        assertThat(OidcSecurityConfiguration.tokenValidator(properties)
                .validate(jwt("https://identity.example/", List.of("different-api"), 300))
                .hasErrors()).isTrue();
        assertThat(OidcSecurityConfiguration.tokenValidator(properties)
                .validate(jwt("https://identity.example/", List.of("inner-cosmos-api"), -300))
                .hasErrors()).isTrue();
    }

    private static Jwt jwt(String issuer, List<String> audience, long expiresInSeconds) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("signed-token-placeholder")
                .header("alg", "RS256")
                .issuer(issuer)
                .subject("subject-123")
                .audience(audience)
                .issuedAt(now.minusSeconds(expiresInSeconds < 0 ? 600 : 30))
                .expiresAt(now.plusSeconds(expiresInSeconds))
                .build();
    }

    private static OidcProperties properties() {
        OidcProperties properties = new OidcProperties();
        properties.setIssuerUri("https://identity.example/");
        properties.setAudience("inner-cosmos-api");
        return properties;
    }
}
