package com.innercosmos.ai.gateway;

import com.innercosmos.common.ErrorCode;
import com.innercosmos.exception.BusinessException;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import jakarta.annotation.PreDestroy;

/**
 * CP-17 gateway governance: the in-flight concurrency gate and the per-call deadline, applied
 * at the {@code StructuredAiService.callObserved} throat (the same single-interception-point
 * pattern as CP-40's {@code ProviderSpendGuard}).
 *
 * <p><b>Congestion gate</b> — bounds the number of concurrent REMOTE provider calls per pod.
 * When the cap is hit the call fails fast with {@link ErrorCode#GATEWAY_BUSY} (429 semantics:
 * transient, retryable, nothing spent) instead of queueing behind slower and slower provider
 * turns. The gate is global rather than per-provider because the throat cannot know which
 * failover candidate actually serves a call — a per-provider cap it cannot attribute would be
 * decorative. Like the spend guard's counters it is per-pod in-memory; the honest fleet-wide
 * version belongs with CP-37/CP-38 infrastructure.
 *
 * <p><b>Per-call deadline</b> — every REMOTE provider interaction (including the JSON-repair
 * retry, which shares one budget) runs under a hard wall-clock deadline. A call that exceeds
 * it is interrupted (thread interrupt + future cancel) and surfaces as
 * {@link GatewayDeadlineExceededException}, which the throat converts into the ordinary
 * failure path: fallback value, FAILED status, no spend recorded — never a fake success from
 * a half-arrived response.
 *
 * <p>The deadline hop runs the blocking provider call on a dedicated daemon pool decorated
 * with {@link ContextPropagatingTaskDecorator} so the HTTP/Aurora Micrometer observation
 * survives the extra asynchronous hop (same discipline as ThreadPoolConfig).
 */
@Component
public class GatewayCallGovernor {

    @Value("${inner-cosmos.ai.gateway.max-concurrency:16}")
    private int maxConcurrency = 16;
    @Value("${inner-cosmos.ai.gateway.call-deadline-ms:120000}")
    private long callDeadlineMs = 120_000;

    private static final ContextPropagatingTaskDecorator CONTEXT_DECORATOR =
            new ContextPropagatingTaskDecorator();

    /** Daemon pool: bounded in practice by the concurrency gate, sized on demand, interruptible. */
    private final ExecutorService deadlineExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "gateway-deadline-" + POOL_SEQ.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicLong POOL_SEQ = new AtomicLong();

    private int gatePermits = -1;
    private Semaphore gate;
    private volatile MeterRegistry meterRegistry;

    @Autowired(required = false)
    void setMeterRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /** In-flight lease; releasing is mandatory on every path (close is idempotent). */
    public static final class CallLease implements AutoCloseable {
        private final Semaphore gate;
        private boolean released;

        private CallLease(Semaphore gate) {
            this.gate = gate;
        }

        @Override
        public void close() {
            synchronized (this) {
                if (released) {
                    return;
                }
                released = true;
            }
            gate.release();
        }
    }

    /**
     * Acquires one in-flight slot or throws {@link BusinessException} with
     * {@link ErrorCode#GATEWAY_BUSY} — 429 semantics, same style as the spend guard's
     * AI_SPEND_EXCEEDED refusal, but explicitly retryable. Must run before the provider call
     * so a congested gateway costs nothing further.
     */
    public CallLease tryAcquire(Long userId, String moduleName) {
        Semaphore current = gate();
        if (!current.tryAcquire()) {
            metric("denied");
            throw new BusinessException(ErrorCode.GATEWAY_BUSY,
                    "网关当前并发已满（in-flight " + inFlight() + "/" + Math.max(1, maxConcurrency)
                            + "），请稍后重试；这次调用没有产生任何消耗。");
        }
        metric("allowed");
        return new CallLease(current);
    }

    /** Absolute wall-clock deadline for a call starting now. */
    public long deadlineAt() {
        return System.currentTimeMillis() + Math.max(1, callDeadlineMs);
    }

    /** Configured budget, exposed for tests and ops introspection. */
    public long deadlineBudgetMs() {
        return Math.max(1, callDeadlineMs);
    }

    /** Current in-flight REMOTE calls (test/ops introspection). */
    public int inFlight() {
        Semaphore current = gate();
        return Math.max(1, maxConcurrency) - current.availablePermits();
    }

    /**
     * Runs one provider interaction under the remaining deadline budget. On timeout the worker
     * future is cancelled with interrupt and {@link GatewayDeadlineExceededException} is thrown
     * (fail-closed: the caller treats it as an ordinary provider failure). Provider exceptions
     * are re-thrown unchanged. The supplier runs on a Runnable decorated with the observation
     * context propagator (the decorator's Callable variant does not exist), completing a
     * {@link java.util.concurrent.CompletableFuture} that carries the result.
     */
    public <T> T runWithDeadline(long deadlineAt, String moduleName, Supplier<T> providerCall) {
        long remaining = deadlineAt - System.currentTimeMillis();
        if (remaining <= 0) {
            metric("deadline_exceeded");
            throw new GatewayDeadlineExceededException(moduleName, 0);
        }
        java.util.concurrent.CompletableFuture<T> completion = new java.util.concurrent.CompletableFuture<>();
        Runnable contextAware = CONTEXT_DECORATOR.decorate(() -> {
            try {
                completion.complete(providerCall.get());
            } catch (Throwable failure) {
                completion.completeExceptionally(failure);
            }
        });
        Future<?> task = deadlineExecutor.submit(contextAware);
        try {
            return completion.get(remaining, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            task.cancel(true);
            metric("deadline_exceeded");
            throw new GatewayDeadlineExceededException(moduleName, remaining);
        } catch (ExecutionException execution) {
            Throwable cause = execution.getCause() == null ? execution : execution.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Provider call failed: " + moduleName, cause);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            task.cancel(true);
            throw new IllegalStateException(
                    "Provider call interrupted before the gateway deadline: " + moduleName, interrupted);
        }
    }

    /**
     * Thrown when a provider call exceeds the gateway per-call deadline. Retryable by nature
     * (transient slowness), and always handled on the failure path — the throat must never
     * turn a deadline kill into a success or swallow it silently.
     */
    public static final class GatewayDeadlineExceededException extends RuntimeException {
        public static final String DETAIL_MARKER = "gateway deadline exceeded";

        GatewayDeadlineExceededException(String moduleName, long waitedMs) {
            super(DETAIL_MARKER + " for " + (moduleName == null ? "unknown" : moduleName)
                    + " after " + waitedMs + "ms budget");
        }
    }

    private synchronized Semaphore gate() {
        int permits = Math.max(1, maxConcurrency);
        if (gate == null || gatePermits != permits) {
            gate = new Semaphore(permits);
            gatePermits = permits;
        }
        return gate;
    }

    private void metric(String outcome) {
        MeterRegistry registry = meterRegistry;
        if (registry == null) {
            return;
        }
        registry.counter("ai.gateway.decisions", "outcome", outcome).increment();
    }

    @PreDestroy
    void shutdown() {
        deadlineExecutor.shutdownNow();
    }
}
