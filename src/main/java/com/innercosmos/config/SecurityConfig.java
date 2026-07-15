package com.innercosmos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.core.env.Environment;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Spring Security configuration for browser/API traffic.
 * - Server-side sessions bridged into Spring Security
 * - Session-bound synchronizer CSRF protection for every unsafe request
 * - External OIDC JWT bearer tokens for native clients; bearer requests do not use cookie CSRF
 * - BCrypt password encoding
 * - H2 console denied in production
 * - Actuator endpoints restricted to admin
 * - All /api/** requires authentication
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           SessionAuthenticationFilter authFilter,
                                           ApiRateLimitFilter rateLimitFilter,
                                           Environment environment,
                                           ObjectProvider<JwtDecoder> jwtDecoder,
                                           @Qualifier("oidcAuthenticationConverter")
                                           ObjectProvider<Converter<Jwt, AbstractAuthenticationToken>> jwtConverter)
            throws Exception {
        boolean csrfEnabled = environment.getProperty(
                "inner-cosmos.security.csrf-enabled", Boolean.class, true);
        boolean oidcEnabled = environment.getProperty(
                "inner-cosmos.auth.oidc.enabled", Boolean.class, false);
        if (csrfEnabled) {
            HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
            RequestMatcher bearerRequest = request -> {
                String authorization = request.getHeader("Authorization");
                return authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7);
            };
            http.csrf(csrf -> csrf.csrfTokenRepository(repository)
                    .ignoringRequestMatchers(bearerRequest));
        } else {
            http.csrf(csrf -> csrf.disable());
        }

        http
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            // SessionAuthenticationFilter rebuilds authentication from the server-owned user ID
            // on every request. Never duplicate the full User entity (including password hash)
            // into HttpSession/Redis via Spring Security's default context repository.
            .securityContext(context -> context.securityContextRepository(
                    new RequestAttributeSecurityContextRepository()))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/csrf").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/plaza/capsules").permitAll()
                .requestMatchers("/api/safety/resources").permitAll()
                .requestMatchers("/h2-console/**").denyAll()
                // Actuator — health/metrics open, write ops require admin
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/metrics/**").permitAll()
                .requestMatchers("/actuator/prometheus").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                // All API requires authentication
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) ->
                        writeError(response, 401, "UNAUTHORIZED", "Authentication is required."))
                .accessDeniedHandler((request, response, exception) -> {
                    String code = exception instanceof CsrfException
                            ? "CSRF_INVALID" : "FORBIDDEN";
                    String message = "CSRF_INVALID".equals(code)
                            ? "A valid CSRF token is required." : "Access is forbidden.";
                    writeError(response, 403, code, message);
                }))
            .addFilterBefore(authFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(rateLimitFilter, BearerTokenAuthenticationFilter.class)
            .headers(headers -> headers
                .frameOptions(f -> f.deny())
                .contentTypeOptions(c -> {})
            );
        if (oidcEnabled) {
            http.oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt
                    .decoder(jwtDecoder.getObject())
                    .jwtAuthenticationConverter(jwtConverter.getObject())));
        }
        return http.build();
    }

    private static void writeError(jakarta.servlet.http.HttpServletResponse response,
                                   int status,
                                   String code,
                                   String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"success\":false,\"error\":\"" + code
                + "\",\"message\":\"" + message + "\",\"status\":" + status + "}");
    }
}
