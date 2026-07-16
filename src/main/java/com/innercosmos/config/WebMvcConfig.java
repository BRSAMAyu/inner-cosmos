package com.innercosmos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;

import java.util.List;

/**
 * Production-grade CORS configuration.
 * Allows configurable origins via environment variable.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * Allowed origins for production.
     * Set via environment variable CORS_ALLOWED_ORIGINS (comma-separated).
     * Fallback: localhost for dev, empty for production (must be set).
     */
    private static final String CORS_ORIGINS = System.getenv("CORS_ALLOWED_ORIGINS") != null
        ? System.getenv("CORS_ALLOWED_ORIGINS")
        : "http://localhost:8080,http://127.0.0.1:8080,https://localhost";

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = List.of(CORS_ORIGINS.split(","));

        registry.addMapping("/api/**")
                .allowedOrigins(origins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("Content-Type", "Accept", "Authorization", "X-CSRF-TOKEN", "X-XSRF-TOKEN",
                        "Idempotency-Key", "If-Match", "Last-Event-ID", "X-Requested-With")
                .exposedHeaders("ETag", "Idempotency-Replayed", "Retry-After", "X-RateLimit-Limit",
                        "X-RateLimit-Remaining")
                .allowCredentials(true)
                .maxAge(3600); // 1 hour preflight cache
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // React is introduced as a vertical slice beside the legacy pages. Keep a
        // stable human-facing entry URL while Vite's generated index remains a
        // normal static resource bundled in the Spring Boot jar.
        registry.addViewController("/app/aurora").setViewName("forward:/app/aurora/index.html");
        registry.addViewController("/app/aurora/").setViewName("forward:/app/aurora/index.html");
    }
}
