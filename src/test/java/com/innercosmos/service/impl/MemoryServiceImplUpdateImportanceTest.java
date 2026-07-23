package com.innercosmos.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.MemoryCard;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.MemoryCardMapper;
import com.innercosmos.service.GravityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Gemini audit 2.1 (CONFIRMED/P0): MemoryCard.versionNo was a plain field that never guarded
 * any write -- both the background gravity recompute (GravityRecalculationServiceImpl /
 * NightlyMemorySettlementJob) and this user-facing updateImportance() wrote the whole entity
 * via updateById(), so whichever write happened last silently won, with no conflict detection.
 * These tests pin MemoryServiceImpl#updateImportance's new field-level conditional update.
 */
class MemoryServiceImplUpdateImportanceTest {

    private static final Long USER_ID = 1L;
    private static final Long CARD_ID = 42L;

    private MemoryCard card(int versionNo) {
        MemoryCard card = new MemoryCard();
        card.id = CARD_ID;
        card.userId = USER_ID;
        card.versionNo = versionNo;
        card.intensityScore = 0.5;
        card.recurrenceCount = 1;
        card.triggerCount = 0;
        return card;
    }

    @Test
    @DisplayName("First attempt succeeds: conditional update guarded on the read versionNo, bumped by one")
    void firstAttemptSucceeds() {
        MemoryCardMapper mapper = mock(MemoryCardMapper.class);
        GravityService gravityService = mock(GravityService.class);
        when(mapper.selectById(CARD_ID)).thenReturn(card(3));
        when(mapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);
        when(gravityService.calculateGravity(anyDouble(), anyInt(), anyDouble(), anyInt(), anyLong())).thenReturn(0.7);

        MemoryServiceImpl service = new MemoryServiceImpl(
                mapper, null, null, null, null, gravityService, null, null, null, null, null, null);

        service.updateImportance(USER_ID, CARD_ID, 0.9);

        verify(mapper).update(eq(null), any(UpdateWrapper.class));
        verify(mapper, never()).updateById(any(MemoryCard.class));
        // Exactly one read: no retry needed when the first attempt wins.
        verify(mapper, times(1)).selectById(CARD_ID);
    }

    @Test
    @DisplayName("Lost the race once (e.g. against a background gravity recompute): retries once against the fresh row and succeeds")
    void losesRaceOnce_retriesAndSucceeds() {
        MemoryCardMapper mapper = mock(MemoryCardMapper.class);
        GravityService gravityService = mock(GravityService.class);
        // First read sees version 3; the conditional update guarded on version=3 loses the race
        // (something else, e.g. a background gravity recompute, already bumped it to 4).
        when(mapper.selectById(CARD_ID)).thenReturn(card(3), card(4));
        when(mapper.update(any(), any(UpdateWrapper.class))).thenReturn(0, 1);
        when(gravityService.calculateGravity(anyDouble(), anyInt(), anyDouble(), anyInt(), anyLong())).thenReturn(0.7);

        MemoryServiceImpl service = new MemoryServiceImpl(
                mapper, null, null, null, null, gravityService, null, null, null, null, null, null);

        service.updateImportance(USER_ID, CARD_ID, 0.9);

        verify(mapper, times(2)).selectById(CARD_ID);
        verify(mapper, times(2)).update(eq(null), any(UpdateWrapper.class));
    }

    @Test
    @DisplayName("Loses the race twice: surfaces a conflict instead of silently dropping the user's edit")
    void losesRaceTwice_surfacesConflict() {
        MemoryCardMapper mapper = mock(MemoryCardMapper.class);
        GravityService gravityService = mock(GravityService.class);
        when(mapper.selectById(CARD_ID)).thenReturn(card(3), card(4));
        when(mapper.update(any(), any(UpdateWrapper.class))).thenReturn(0, 0);
        when(gravityService.calculateGravity(anyDouble(), anyInt(), anyDouble(), anyInt(), anyLong())).thenReturn(0.7);

        MemoryServiceImpl service = new MemoryServiceImpl(
                mapper, null, null, null, null, gravityService, null, null, null, null, null, null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateImportance(USER_ID, CARD_ID, 0.9));
        assertEquals(ErrorCode.CONFLICT, ex.code);
        // Never falls back to a whole-entity overwrite.
        verify(mapper, never()).updateById(any(MemoryCard.class));
    }

    @Test
    @DisplayName("Rejects a foreign-owned memory card without ever attempting a write")
    void rejectsForeignCard() {
        MemoryCardMapper mapper = mock(MemoryCardMapper.class);
        MemoryCard foreignCard = card(3);
        foreignCard.userId = 999L;
        when(mapper.selectById(CARD_ID)).thenReturn(foreignCard);

        MemoryServiceImpl service = new MemoryServiceImpl(
                mapper, null, null, null, null, mock(GravityService.class), null, null, null, null, null, null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateImportance(USER_ID, CARD_ID, 0.9));
        assertEquals(ErrorCode.UNAUTHORIZED, ex.code);
        verify(mapper, never()).update(any(), any());
        verify(mapper, never()).updateById(any(MemoryCard.class));
    }
}
