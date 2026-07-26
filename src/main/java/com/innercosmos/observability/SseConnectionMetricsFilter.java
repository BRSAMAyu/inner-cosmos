package com.innercosmos.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Privacy-safe, low-cardinality lifecycle metrics for the three SSE surfaces.
 * No user/session/turn/message value is ever attached to a meter.
 */
@Component
public class SseConnectionMetricsFilter extends OncePerRequestFilter {
    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, AtomicInteger> active = new ConcurrentHashMap<>();

    public SseConnectionMetricsFilter(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return route(request.getRequestURI()) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String route = route(request.getRequestURI());
        String resume = Boolean.toString(request.getHeader("Last-Event-ID") != null
                || request.getParameter("afterSequence") != null);
        ConnectionSample sample = open(route, resume);
        try {
            filterChain.doFilter(request, response);
            if (request.isAsyncStarted()) {
                request.getAsyncContext().addListener(new AsyncListener() {
                    @Override public void onComplete(AsyncEvent event) { sample.close("complete"); }
                    @Override public void onTimeout(AsyncEvent event) { sample.close("timeout"); }
                    @Override public void onError(AsyncEvent event) { sample.close("error"); }
                    @Override public void onStartAsync(AsyncEvent event) {
                        event.getAsyncContext().addListener(this);
                    }
                });
            } else {
                sample.close(response.getStatus() >= 400 ? "rejected" : "sync");
            }
        } catch (IOException | ServletException | RuntimeException failure) {
            sample.close("error");
            throw failure;
        }
    }

    ConnectionSample open(String route, String resume) {
        Tags tags = Tags.of("route", route, "resume", resume);
        String key = route + "|" + resume;
        AtomicInteger gauge = active.computeIfAbsent(key, ignored -> {
            AtomicInteger value = new AtomicInteger();
            Gauge.builder("inner.cosmos.sse.connections.active", value, AtomicInteger::get)
                    .tags(tags).register(registry);
            return value;
        });
        gauge.incrementAndGet();
        registry.counter("inner.cosmos.sse.connections.total", tags).increment();
        return new ConnectionSample(gauge, tags, registry);
    }

    static String route(String uri) {
        if (uri == null) return null;
        if (uri.matches(".*/api(?:/v1)?/aurora/turns/[^/]+/events$")) return "aurora_replay";
        if (uri.matches(".*/api(?:/v1)?/aurora/stream$")) return "aurora_live";
        if (uri.matches(".*/api/proactive/stream$")) return "proactive";
        return null;
    }

    static final class ConnectionSample {
        private final AtomicInteger active;
        private final Tags tags;
        private final MeterRegistry registry;
        private final long startedNanos = System.nanoTime();
        private final AtomicBoolean closed = new AtomicBoolean();

        ConnectionSample(AtomicInteger active, Tags tags, MeterRegistry registry) {
            this.active = active;
            this.tags = tags;
            this.registry = registry;
        }

        void close(String outcome) {
            if (!closed.compareAndSet(false, true)) return;
            active.decrementAndGet();
            Counter.builder("inner.cosmos.sse.connections.closed")
                    .tags(tags.and("outcome", outcome)).register(registry).increment();
            Timer.builder("inner.cosmos.sse.connection.duration")
                    .tags(tags.and("outcome", outcome)).register(registry)
                    .record(Duration.ofNanos(Math.max(0L, System.nanoTime() - startedNanos)));
        }
    }
}
