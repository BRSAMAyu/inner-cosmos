package com.innercosmos.event;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.service.GravityService;
import com.innercosmos.service.GravityTimePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Gemini audit 1.5/2.1 (P1/P0): this listener used to inline its own gravity formula and a
 * hardcoded 30-day fallback (divergent from NightlyMemorySettlementJob's own inline copy), and
 * wrote the recomputed gravity via a whole-entity updateById() with no conflict guard. Both are
 * fixed by routing through the shared GravityService/GravityTimePolicy and an atomic
 * versionNo-guarded conditional update.
 */
class GravityRecalculateListenerTest {

    private static final Long USER_ID = 1L;

    private MemoryCard card(long id, int versionNo) {
        MemoryCard card = new MemoryCard();
        card.id = id;
        card.userId = USER_ID;
        card.versionNo = versionNo;
        card.status = "ACTIVE";
        return card;
    }

    @Test
    @DisplayName("Recomputes gravity via the shared policy/service and writes a field-level conditional update guarded on versionNo")
    void recomputesAndWritesConditionally() {
        MemoryCardMapper mapper = mock(MemoryCardMapper.class);
        GravityService gravityService = mock(GravityService.class);
        GravityTimePolicy timePolicy = mock(GravityTimePolicy.class);
        MemoryCard theCard = card(10L, 2);
        when(mapper.selectList(any())).thenReturn(List.of(theCard));
        when(timePolicy.daysSinceAnchor(theCard)).thenReturn(5L);
        when(gravityService.calculateGravity(0.0, 0, 0.0, 0, 5L)).thenReturn(0.42);
        when(mapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);

        GravityRecalculateListener listener = new GravityRecalculateListener(mapper, gravityService, timePolicy);
        listener.onDialogFinished(new DialogFinishedEvent(USER_ID, 100L));

        // Uses the shared policy for the anchor -- not an inline hardcoded fallback.
        verify(timePolicy).daysSinceAnchor(theCard);
        verify(gravityService).calculateGravity(0.0, 0, 0.0, 0, 5L);
        // Field-level conditional update, never a whole-entity overwrite.
        verify(mapper).update(eq(null), any(UpdateWrapper.class));
        verify(mapper, never()).updateById(any(MemoryCard.class));
    }

    @Test
    @DisplayName("Card changed concurrently since the batch was read (e.g. a user importance edit): the conditional update loses and is NOT retried as an overwrite")
    void concurrentChange_doesNotOverwrite() {
        MemoryCardMapper mapper = mock(MemoryCardMapper.class);
        GravityService gravityService = mock(GravityService.class);
        GravityTimePolicy timePolicy = mock(GravityTimePolicy.class);
        MemoryCard theCard = card(11L, 2);
        when(mapper.selectList(any())).thenReturn(List.of(theCard));
        when(timePolicy.daysSinceAnchor(any())).thenReturn(1L);
        when(gravityService.calculateGravity(anyDouble(), anyInt(), anyDouble(), anyInt(), anyLong())).thenReturn(0.5);
        // 0 rows: the card's version_no no longer matches (something else, e.g. a user edit,
        // already bumped it) -- the listener must accept this and move on, not force-write.
        when(mapper.update(any(), any(UpdateWrapper.class))).thenReturn(0);

        GravityRecalculateListener listener = new GravityRecalculateListener(mapper, gravityService, timePolicy);
        listener.onDialogFinished(new DialogFinishedEvent(USER_ID, 100L));

        verify(mapper).update(eq(null), any(UpdateWrapper.class));
        // Exactly one attempt -- a lost race here is not retried within this event; the next
        // dialog-finished event or nightly run will recompute against the fresh row.
        verify(mapper, times(1)).update(any(), any(UpdateWrapper.class));
        verify(mapper, never()).updateById(any(MemoryCard.class));
    }

    @Test
    @DisplayName("An exception during processing is caught and logged, not propagated")
    void exceptionIsCaughtNotPropagated() {
        MemoryCardMapper mapper = mock(MemoryCardMapper.class);
        GravityService gravityService = mock(GravityService.class);
        GravityTimePolicy timePolicy = mock(GravityTimePolicy.class);
        when(mapper.selectList(any())).thenThrow(new RuntimeException("db down"));

        GravityRecalculateListener listener = new GravityRecalculateListener(mapper, gravityService, timePolicy);

        // Must not throw -- this runs @Async, fallbackExecution=true, after commit; an uncaught
        // exception here must never propagate back into the triggering request.
        listener.onDialogFinished(new DialogFinishedEvent(USER_ID, 100L));
    }
}
