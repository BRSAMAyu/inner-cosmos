package com.innercosmos.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Actuator and metrics configuration for production monitoring.
 */
@Configuration
public class ActuatorConfig {

    /**
     * Tags all metrics with the service name for Prometheus/Grafana identification.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
            .commonTags("application", "inner-cosmos")
            .commonTags("service", "aurora-ai-companion");
    }

    /**
     * Dedicated thread pool for async LLM calls — prevents blocking the main thread pool.
     */
    @Bean(name = "llmTaskExecutor")
    public Executor llmTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("llm-");
        executor.setRejectedExecutionHandler((r, e) -> {
            // Log and drop when queue is full — LLM calls should be resilient
            System.err.println("LLM task rejected: queue full");
        });
        executor.initialize();
        return executor;
    }
}