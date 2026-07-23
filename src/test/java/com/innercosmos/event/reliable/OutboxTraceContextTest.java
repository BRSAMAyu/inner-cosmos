package com.innercosmos.event.reliable;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxTraceContextTest {

    @Test
    void capturesOnlyAStandardsCompliantTraceParent() {
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext current = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(current);
        when(current.traceId()).thenReturn("0123456789abcdef0123456789abcdef");
        when(current.spanId()).thenReturn("0123456789abcdef");
        when(current.sampled()).thenReturn(true);

        assertThat(new OutboxTraceContext(tracer).capture())
                .isEqualTo("00-0123456789abcdef0123456789abcdef-0123456789abcdef-01");
    }

    @Test
    void parsesTraceParentAsRemoteConsumerParentAndRejectsLegacyBareIds() {
        Tracer tracer = mock(Tracer.class);
        TraceContext.Builder builder = mock(TraceContext.Builder.class);
        TraceContext parent = mock(TraceContext.class);
        when(tracer.traceContextBuilder()).thenReturn(builder);
        when(builder.traceId("0123456789abcdef0123456789abcdef")).thenReturn(builder);
        when(builder.spanId("0123456789abcdef")).thenReturn(builder);
        when(builder.sampled(true)).thenReturn(builder);
        when(builder.build()).thenReturn(parent);
        OutboxTraceContext context = new OutboxTraceContext(tracer);

        assertThat(context.parse("00-0123456789abcdef0123456789abcdef-0123456789abcdef-01"))
                .isSameAs(parent);
        assertThat(context.parse("0123456789abcdef0123456789abcdef")).isNull();
        verify(builder).traceId("0123456789abcdef0123456789abcdef");
        verify(builder).spanId("0123456789abcdef");
        verify(builder).sampled(true);
    }

    @Test
    void consumerSpanUsesOnlyBoundedOperationalAttributes() {
        Tracer tracer = mock(Tracer.class);
        Span.Builder builder = mock(Span.Builder.class);
        Span span = mock(Span.class);
        when(tracer.spanBuilder()).thenReturn(builder);
        when(builder.name("inner.cosmos.outbox.consume")).thenReturn(builder);
        when(builder.kind(Span.Kind.CONSUMER)).thenReturn(builder);
        when(builder.tag("messaging.system", "jdbc-outbox")).thenReturn(builder);
        when(builder.tag("messaging.operation", "process")).thenReturn(builder);
        when(builder.tag("event.type", "dialog.finished.v1")).thenReturn(builder);
        when(builder.tag("event.schema", "1")).thenReturn(builder);
        when(builder.setNoParent()).thenReturn(builder);
        when(builder.start()).thenReturn(span);

        OutboxEvent event = new OutboxEvent(9L, UUID.randomUUID(), "private-dedup",
                "dialog-session", "private-user-session", "dialog.finished.v1", 1,
                "{\"message\":\"must-not-be-a-span-attribute\"}", null, 0, "worker",
                LocalDateTime.now().plusSeconds(30));

        assertThat(new OutboxTraceContext(tracer).startConsumer(event)).isSameAs(span);
        verify(builder).tag("event.type", "dialog.finished.v1");
        verify(builder).tag("event.schema", "1");
        verify(builder).setNoParent();
    }
}
