package com.innercosmos.event;

import com.innercosmos.entity.MemoryCard;
import com.innercosmos.service.GravityRecalculationService;
import com.innercosmos.service.MemoryService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Gemini audit 1.6 (CONFIRMED/P1): this listener used to run alongside a SEPARATE, independent
 * {@code @Async} listener (the deleted GravityRecalculateListener) that recomputed gravity for
 * the user's whole active card set on the SAME DialogFinishedEvent, with no guarantee Spring
 * would complete them in any particular order -- the gravity recompute could run BEFORE this
 * listener's extraction committed, missing the just-created/updated card. These tests pin the
 * fix: extraction and gravity recompute are now ONE ordered sequence inside this single listener.
 */
class MemoryExtractListenerTest {

    private final MemoryService memoryService = mock(MemoryService.class);
    private final GravityRecalculationService gravityRecalculationService = mock(GravityRecalculationService.class);
    private final MemoryExtractListener listener = new MemoryExtractListener(memoryService, gravityRecalculationService);

    @Test
    void extractionAlwaysRunsBeforeGravityRecompute() {
        when(memoryService.extractFromSession(7L, 19L)).thenReturn(new MemoryCard());

        listener.onDialogFinished(new DialogFinishedEvent(7L, 19L));

        var ordered = inOrder(memoryService, gravityRecalculationService);
        ordered.verify(memoryService).extractFromSession(7L, 19L);
        ordered.verify(gravityRecalculationService).recalculateForUser(7L);
    }

    @Test
    void gravityRecomputeStillRunsForTheUsersOtherCardsEvenWhenExtractionFails() {
        // The gravity recompute covers the user's WHOLE active card set, not just the card this
        // extraction would have produced -- those other cards do not depend on this extraction
        // succeeding, and must not miss their decay refresh just because this step failed (this
        // matches the original two-listener design's failure isolation; only the ORDERING changed).
        when(memoryService.extractFromSession(any(), any())).thenThrow(new RuntimeException("db unavailable"));

        listener.onDialogFinished(new DialogFinishedEvent(7L, 19L));

        verify(gravityRecalculationService).recalculateForUser(7L);
    }

    @Test
    void anExceptionFromEitherStepIsCaughtNotPropagated() {
        when(memoryService.extractFromSession(any(), any())).thenReturn(new MemoryCard());
        doThrow(new RuntimeException("gravity recompute blew up"))
                .when(gravityRecalculationService).recalculateForUser(any());

        // Must not throw -- this runs @Async, fallbackExecution=true, after commit; an uncaught
        // exception here must never propagate back into the triggering request.
        listener.onDialogFinished(new DialogFinishedEvent(7L, 19L));
    }
}
