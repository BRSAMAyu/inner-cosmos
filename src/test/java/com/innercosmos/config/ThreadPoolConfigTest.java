package com.innercosmos.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThreadPoolConfigTest {
    @Test
    void configuredClassroomCapacityIsNotSilentlyReplacedByTheOldEightThreadCeiling() {
        Executor configured = new ThreadPoolConfig().aiExecutor(4, 20, 100);
        ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) configured;
        try {
            assertEquals(4, pool.getCorePoolSize());
            assertEquals(20, pool.getMaxPoolSize());
            assertEquals(100, pool.getQueueCapacity());
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void neverAllowsMaxPoolSizeBelowCorePoolSize() {
        ThreadPoolTaskExecutor pool =
                (ThreadPoolTaskExecutor) new ThreadPoolConfig().aiExecutor(12, 8, 0);
        try {
            assertEquals(12, pool.getCorePoolSize());
            assertEquals(12, pool.getMaxPoolSize());
            assertEquals(0, pool.getQueueCapacity());
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void normalizesInvalidNonPositivePoolSizesToAUsableExecutor() {
        ThreadPoolTaskExecutor pool =
                (ThreadPoolTaskExecutor) new ThreadPoolConfig().aiExecutor(-4, 0, -10);
        try {
            assertEquals(1, pool.getCorePoolSize());
            assertEquals(1, pool.getMaxPoolSize());
            assertEquals(0, pool.getQueueCapacity());
        } finally {
            pool.shutdown();
        }
    }
}
