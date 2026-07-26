package com.innercosmos.config;

import com.innercosmos.ai.runtime.AuroraDualKernelRuntime;
import com.innercosmos.ai.structured.StructuredAiService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;

class PlannerExecutorIsolationTest {

    @Test
    void saturatedPlannerPoolRejectsInsteadOfRunningOnForegroundCaller() throws Exception {
        ThreadPoolConfig config = new ThreadPoolConfig();
        ThreadPoolTaskExecutor ai = (ThreadPoolTaskExecutor) config.aiExecutor(1, 1, 0);
        ThreadPoolTaskExecutor planner = (ThreadPoolTaskExecutor) config.plannerExecutor(1, 1, 1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch running = new CountDownLatch(1);
        try {
            planner.execute(() -> await(release, running));
            running.await(1, TimeUnit.SECONDS);
            planner.execute(() -> await(release, new CountDownLatch(0))); // fills the one-slot queue

            assertNotSame(ai, planner);
            assertThrows(RejectedExecutionException.class, () -> planner.execute(() -> {}));
            assertTimeoutPreemptively(Duration.ofMillis(500),
                    () -> assertDoesNotThrow(() -> CompletableFuture.supplyAsync(() -> "speaker", ai).join()));
        } finally {
            release.countDown();
            planner.shutdown();
            ai.shutdown();
        }
    }

    @Test
    void springWiresAuroraRuntimeToPlannerExecutorNotAiExecutor() {
        new ApplicationContextRunner()
                .withUserConfiguration(ThreadPoolConfig.class, RuntimeOnlyConfig.class)
                .run(context -> {
                    AuroraDualKernelRuntime runtime = context.getBean(AuroraDualKernelRuntime.class);
                    Object injected = ReflectionTestUtils.getField(runtime, "plannerExecutor");
                    assertSame(context.getBean("plannerExecutor"), injected);
                    assertNotSame(context.getBean("aiExecutor"), injected);
                });
    }

    private static void await(CountDownLatch release, CountDownLatch running) {
        running.countDown();
        try {
            release.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Configuration
    static class RuntimeOnlyConfig {
        @Bean
        StructuredAiService structuredAiService() {
            return mock(StructuredAiService.class);
        }

        @Bean
        AuroraDualKernelRuntime auroraDualKernelRuntime(StructuredAiService structuredAiService) {
            return new AuroraDualKernelRuntime(structuredAiService);
        }
    }
}