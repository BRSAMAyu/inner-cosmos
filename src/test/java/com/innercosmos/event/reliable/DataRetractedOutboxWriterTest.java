package com.innercosmos.event.reliable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.event.DataRetractedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A5: the durable data.retracted.v1 outbox writer must append exactly one sensitive-free row per
 * receipt, keyed for idempotency by the receipt id, with the declared event type/schema version.
 */
class DataRetractedOutboxWriterTest {

    @Test
    void appendsOneSensitiveFreeOutboxRowKeyedByReceiptId() throws Exception {
        JdbcOutboxRepository repository = mock(JdbcOutboxRepository.class);
        when(repository.append(any(), any(), any(), any(), any(), anyInt(), any(), any())).thenReturn(true);
        DataRetractedOutboxWriter writer = new DataRetractedOutboxWriter(
                repository, new ObjectMapper(), new OutboxTraceContext(io.micrometer.tracing.Tracer.NOOP));

        writer.onDataRetracted(new DataRetractedEvent(42L, 7L, "CAPSULE", 5L,
                "CAPSULE_MATCH_INDEX", "ERASED", 1));

        ArgumentCaptor<String> dedup = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(repository).append(any(UUID.class), dedup.capture(), eq("data-retraction"), eq("42"),
                eq("data.retracted.v1"), eq(1), payload.capture(), any());

        assertEquals("data-retraction:42:v1", dedup.getValue());
        String json = payload.getValue();
        assertTrue(json.contains("\"receiptId\":42"), json);
        assertTrue(json.contains("\"derivativeType\":\"CAPSULE_MATCH_INDEX\""), json);
        assertTrue(json.contains("\"action\":\"ERASED\""), json);
        assertTrue(json.contains("\"affectedCount\":1"), json);
        // Sensitive-free: the payload carries ids/enums/counts only, never memory/persona content.
        assertTrue(json.startsWith("{") && !json.toLowerCase().contains("password"), json);
    }
}
