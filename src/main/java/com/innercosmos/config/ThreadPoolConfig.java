package com.innercosmos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@EnableAsync
@Configuration
public class ThreadPoolConfig {
    /** Model work never shares worker threads with SSE request orchestration. */
    @Bean(name = "aiExecutor")
    @Primary
    public Executor aiExecutor(
            @Value("${inner-cosmos.executors.ai.core-size:8}") int coreSize,
            @Value("${inner-cosmos.executors.ai.max-size:32}") int maxSize,
            @Value("${inner-cosmos.executors.ai.queue-capacity:0}") int queueCapacity) {
        return buildExecutor(coreSize, maxSize, queueCapacity, "inner-cosmos-ai-");
    }

    /**
     * Slow, background-only Aurora planning never consumes foreground model workers. The bounded
     * queue protects the classroom host during bursts; AbortPolicy is intentional so saturation
     * becomes explicit FAILED evidence instead of CallerRunsPolicy moving a 45-second planner
     * onto the user-facing speaker thread.
     */
    @Bean(name = "plannerExecutor")
    public Executor plannerExecutor(
            @Value("${inner-cosmos.executors.planner.core-size:4}") int coreSize,
            @Value("${inner-cosmos.executors.planner.max-size:8}") int maxSize,
            @Value("${inner-cosmos.executors.planner.queue-capacity:8}") int queueCapacity) {
        return buildExecutor(coreSize, maxSize, queueCapacity, "inner-cosmos-planner-",
                new ThreadPoolExecutor.AbortPolicy());
    }
    /** Legacy @Async projections keep their own back-pressured queue. */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor(
            @Value("${spring.task.execution.pool.core-size:4}") int coreSize,
            @Value("${spring.task.execution.pool.max-size:20}") int maxSize,
            @Value("${spring.task.execution.pool.queue-capacity:100}") int queueCapacity) {
        return buildExecutor(coreSize, maxSize, queueCapacity, "inner-cosmos-task-");
    }

    /** Long-lived SSE orchestration may wait for model futures, so it must use an isolated pool. */
    @Bean(name = "sseExecutor")
    public Executor sseExecutor(
            @Value("${inner-cosmos.executors.sse.core-size:8}") int coreSize,
            @Value("${inner-cosmos.executors.sse.max-size:64}") int maxSize,
            @Value("${inner-cosmos.executors.sse.queue-capacity:0}") int queueCapacity) {
        return buildExecutor(coreSize, maxSize, queueCapacity, "inner-cosmos-sse-");
    }

    private Executor buildExecutor(int coreSize, int maxSize, int queueCapacity, String prefix) {
        return buildExecutor(coreSize, maxSize, queueCapacity, prefix,
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    private Executor buildExecutor(int coreSize, int maxSize, int queueCapacity, String prefix,
                                   java.util.concurrent.RejectedExecutionHandler rejectedHandler) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int normalizedCoreSize = Math.max(1, coreSize);
        executor.setCorePoolSize(normalizedCoreSize);
        executor.setMaxPoolSize(Math.max(normalizedCoreSize, maxSize));
        executor.setQueueCapacity(Math.max(0, queueCapacity));
        executor.setThreadNamePrefix(prefix);
        // Preserve the current HTTP/Aurora Micrometer observation across every asynchronous hop.
        // Without this decorator SSE orchestration and provider work start unrelated root traces.
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        executor.setRejectedExecutionHandler(rejectedHandler);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
