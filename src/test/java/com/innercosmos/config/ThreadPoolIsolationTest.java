package com.innercosmos.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class ThreadPoolIsolationTest {
    @Test
    void eightConcurrentSseOrchestratorsCannotStarveTheirModelChildren() {
        ThreadPoolConfig config = new ThreadPoolConfig();
        ThreadPoolTaskExecutor ai = (ThreadPoolTaskExecutor) config.aiExecutor(8, 8, 0);
        ThreadPoolTaskExecutor sse = (ThreadPoolTaskExecutor) config.sseExecutor(8, 8, 0);
        try {
            assertNotSame(ai, sse);
            CountDownLatch outersStarted = new CountDownLatch(8);
            List<CompletableFuture<Void>> streams = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                streams.add(CompletableFuture.runAsync(() -> {
                    outersStarted.countDown();
                    try {
                        if (!outersStarted.await(1, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("not all SSE workers started");
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(interrupted);
                    }
                    CompletableFuture.supplyAsync(() -> "model-reply", ai).join();
                }, sse));
            }
            assertTimeoutPreemptively(Duration.ofSeconds(2),
                    () -> CompletableFuture.allOf(streams.toArray(CompletableFuture[]::new)).join());
        } finally {
            sse.shutdown();
            ai.shutdown();
        }
    }
}
