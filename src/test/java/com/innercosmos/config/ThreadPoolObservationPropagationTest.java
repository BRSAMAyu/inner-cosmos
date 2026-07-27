package com.innercosmos.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import io.micrometer.observation.tck.TestObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertSame;

class ThreadPoolObservationPropagationTest {

    @Test
    void sseAndAiExecutorHopsPreserveTheSubmittingObservation() throws Exception {
        TestObservationRegistry registry = TestObservationRegistry.create();
        ObservationThreadLocalAccessor accessor = ObservationThreadLocalAccessor.getInstance();
        accessor.setObservationRegistry(registry);
        ThreadPoolConfig config = new ThreadPoolConfig();
        ThreadPoolTaskExecutor sse = (ThreadPoolTaskExecutor) config.sseExecutor(1, 1, 0);
        ThreadPoolTaskExecutor ai = (ThreadPoolTaskExecutor) config.aiExecutor(1, 1, 0);
        try {
            Observation http = Observation.start("http.server.requests", registry);
            try (var ignored = http.openScope()) {
                CompletableFuture<Observation> observed = CompletableFuture.supplyAsync(
                        () -> CompletableFuture.supplyAsync(registry::getCurrentObservation, ai).join(),
                        sse);
                assertSame(http, observed.get(2, TimeUnit.SECONDS));
            } finally {
                http.stop();
            }
        } finally {
            sse.shutdown();
            ai.shutdown();
            accessor.setObservationRegistry(io.micrometer.observation.ObservationRegistry.NOOP);
        }
    }
}
