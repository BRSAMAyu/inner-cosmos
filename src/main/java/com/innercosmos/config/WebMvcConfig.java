package com.innercosmos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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
        : "http://localhost:8080,http://127.0.0.1:8080";

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = List.of(CORS_ORIGINS.split(","));

        registry.addMapping("/api/**")
                .allowedOrigins(origins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600); // 1 hour preflight cache
    }
}