package com.innercosmos.config;

import com.innercosmos.entity.User;
import com.innercosmos.service.OidcIdentityService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.List;

@Configuration
@EnableConfigurationProperties(OidcProperties.class)
public class OidcSecurityConfiguration {

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    @ConditionalOnProperty(prefix = "inner-cosmos.auth.oidc", name = "enabled", havingValue = "true")
    JwtDecoder oidcJwtDecoder(OidcProperties properties) {
        require(properties.getIssuerUri(), "issuer-uri");
        require(properties.getJwkSetUri(), "jwk-set-uri");
        require(properties.getAudience(), "audience");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.getJwkSetUri()).build();
        decoder.setJwtValidator(tokenValidator(properties));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> tokenValidator(OidcProperties properties) {
        OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer(properties.getIssuerUri());
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>("aud",
                values -> values != null && values.contains(properties.getAudience()));
        return new DelegatingOAuth2TokenValidator<>(issuer, audience);
    }

    @Bean
    @ConditionalOnProperty(prefix = "inner-cosmos.auth.oidc", name = "enabled", havingValue = "true")
    Converter<Jwt, AbstractAuthenticationToken> oidcAuthenticationConverter(OidcIdentityService identities) {
        return jwt -> {
            User user = identities.resolve(jwt);
            var authorities = "ADMIN".equals(user.role)
                    ? List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))
                    : List.of(new SimpleGrantedAuthority("ROLE_USER"));
            return new UsernamePasswordAuthenticationToken(user, null, authorities);
        };
    }

    private static void require(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("OIDC is enabled but " + property + " is not configured");
        }
    }
}
