package com.innercosmos.event.reliable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.service.ClaimCandidateService;
import com.innercosmos.service.EmotionTimelineService;
import com.innercosmos.service.MemoryService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DialogFinishedProjectionHandlerTest {
    private final MemoryService memoryService = mock(MemoryService.class);
    private final ClaimCandidateService claimCandidateService = mock(ClaimCandidateService.class);
    private final EmotionTimelineService emotionTimelineService = mock(EmotionTimelineService.class);
    private final DialogFinishedProjectionHandler handler = new DialogFinishedProjectionHandler(
            new ObjectMapper(), memoryService, claimCandidateService, emotionTimelineService);

    @Test
    void projectsMemoryClaimsAndEmotionTimelineInOneFailFastHandler() {
        handler.handle(event("{\"userId\":7,\"sessionId\":19}"));

        var ordered = inOrder(memoryService, claimCandidateService, emotionTimelineService);
        ordered.verify(memoryService).extractFromSession(7L, 19L);
        ordered.verify(claimCandidateService).stageForSession(7L, 19L);
        ordered.verify(emotionTimelineService).aggregateFromTraces(org.mockito.ArgumentMatchers.eq(7L), any());
        assertThat(handler.eventType()).isEqualTo(DialogFinishedOutboxWriter.EVENT_TYPE);
        assertThat(handler.consumerName()).isEqualTo("dialog-finished-projection-v1");
    }

    @Test
    void malformedPayloadFailsBeforeAnyPrivateDataRead() {
        assertThatThrownBy(() -> handler.handle(event("{\"userId\":7}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identifiers");

        verify(memoryService, never()).extractFromSession(any(), any());
        verify(claimCandidateService, never()).stageForSession(any(), any());
        verify(emotionTimelineService, never()).aggregateFromTraces(any(), any());
    }

    @Test
    void projectionFailurePropagatesSoOutboxTransactionCanRetry() {
        when(memoryService.extractFromSession(7L, 19L)).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> handler.handle(event("{\"userId\":7,\"sessionId\":19}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        verify(claimCandidateService, never()).stageForSession(any(), any());
        verify(emotionTimelineService, never()).aggregateFromTraces(any(), any());
    }

    private OutboxEvent event(String payload) {
        return new OutboxEvent(1L, UUID.randomUUID(), "dialog-session:19:finished:v1",
                "dialog-session", "19", DialogFinishedOutboxWriter.EVENT_TYPE, 1,
                payload, "trace", 0, "worker", LocalDateTime.now().plusSeconds(30));
    }
}
