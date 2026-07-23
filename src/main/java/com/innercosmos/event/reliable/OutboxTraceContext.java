package com.innercosmos.event.reliable;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Propagates only W3C trace context through the transactional outbox.
 *
 * <p>The outbox column deliberately stores no baggage, user identifier, prompt, message,
 * retrieval result or aggregate identifier. Older rows containing a bare trace id are accepted
 * as unparented work rather than guessed into an invalid parent span.
 */
@Component
public class OutboxTraceContext {
    private static final Pattern TRACE_PARENT = Pattern.compile(
            "^00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$");

    private final Tracer tracer;

    public OutboxTraceContext(Tracer tracer) {
        this.tracer = tracer;
    }

    public String capture() {
        Span current = tracer.currentSpan();
        if (current == null || current.context() == null) {
            // Compatibility with deployments that had log correlation before a tracing bridge.
            return MDC.get("traceId");
        }
        TraceContext context = current.context();
        if (!validHex(context.traceId(), 32) || !validHex(context.spanId(), 16)) return null;
        String flags = Boolean.FALSE.equals(context.sampled()) ? "00" : "01";
        return "00-" + context.traceId().toLowerCase(Locale.ROOT)
                + "-" + context.spanId().toLowerCase(Locale.ROOT) + "-" + flags;
    }

    public Span startConsumer(OutboxEvent event) {
        Span.Builder builder = tracer.spanBuilder()
                .name("inner.cosmos.outbox.consume")
                .kind(Span.Kind.CONSUMER)
                .tag("messaging.system", "jdbc-outbox")
                .tag("messaging.operation", "process")
                .tag("event.type", bounded(event.eventType()))
                .tag("event.schema", Integer.toString(event.schemaVersion()));
        TraceContext parent = parse(event.traceId());
        if (parent == null) builder.setNoParent();
        else builder.setParent(parent);
        return builder.start();
    }

    TraceContext parse(String value) {
        if (value == null) return null;
        Matcher matcher = TRACE_PARENT.matcher(value.trim().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) return null;
        return tracer.traceContextBuilder()
                .traceId(matcher.group(1))
                .spanId(matcher.group(2))
                .sampled((Integer.parseInt(matcher.group(3), 16) & 1) == 1)
                .build();
    }

    private static boolean validHex(String value, int length) {
        return value != null && value.length() == length && value.matches("[0-9a-fA-F]+");
    }

    private static String bounded(String value) {
        if (value == null || !value.matches("[a-z0-9._-]{1,64}")) return "unknown";
        return value;
    }
}
