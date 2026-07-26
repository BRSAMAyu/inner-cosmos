package com.innercosmos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@EnableAsync
@Configuration
public class ThreadPoolConfig {
    @Bean(name = {"taskExecutor", "aiExecutor"})
    public Executor aiExecutor(
            @Value("${spring.task.execution.pool.core-size:4}") int coreSize,
            @Value("${spring.task.execution.pool.max-size:20}") int maxSize,
            @Value("${spring.task.execution.pool.queue-capacity:100}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int normalizedCoreSize = Math.max(1, coreSize);
        executor.setCorePoolSize(normalizedCoreSize);
        executor.setMaxPoolSize(Math.max(normalizedCoreSize, maxSize));
        executor.setQueueCapacity(Math.max(0, queueCapacity));
        executor.setThreadNamePrefix("inner-cosmos-ai-");
        // Legacy @Async projections are not all outbox-backed yet. When a classroom burst fills
        // the queue, applying caller-side backpressure is safer than AbortPolicy rejecting and
        // losing a memory/profile extraction task after the foreground request already succeeded.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
