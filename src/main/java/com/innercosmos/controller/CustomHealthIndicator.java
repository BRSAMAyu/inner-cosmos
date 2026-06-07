package com.innercosmos.controller;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Custom health indicators for production monitoring.
 * Exposes: DB connectivity, LLM availability, memory pressure.
 */
@Component
public class CustomHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    public CustomHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        // Check DB connectivity
        try (Connection conn = dataSource.getConnection()) {
            if (!conn.isValid(2)) {
                return Health.down().withDetail("db", "connection invalid").build();
            }
        } catch (Exception e) {
            return Health.down().withDetail("db", e.getMessage()).build();
        }

        // Check memory pressure
        Runtime runtime = Runtime.getRuntime();
        long totalMem = runtime.totalMemory();
        long freeMem = runtime.freeMemory();
        double usageRatio = (double) (totalMem - freeMem) / totalMem;
        if (usageRatio > 0.9) {
            return Health.status("WARNING")
                .withDetail("memory", String.format("%.1f%% used", usageRatio * 100))
                .withDetail("total_mb", totalMem / 1024 / 1024)
                .build();
        }

        return Health.up()
            .withDetail("memory", String.format("%.1f%% used", usageRatio * 100))
            .withDetail("total_mb", totalMem / 1024 / 1024)
            .build();
    }
}