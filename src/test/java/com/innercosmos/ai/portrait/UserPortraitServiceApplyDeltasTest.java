package com.innercosmos.ai.portrait;

import com.innercosmos.ai.portrait.dto.PortraitDeltas;
import com.innercosmos.entity.UserPortrait;
import com.innercosmos.entity.UserPortraitHistory;
import com.innercosmos.mapper.UserPortraitHistoryMapper;
import com.innercosmos.mapper.UserPortraitMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * IC-DATA-003: data-hygiene tests for {@link UserPortraitService#applyDeltas}.
 *
 * The LLM is asked for score/confidence in [0,1] and a non-blank valueJson,
 * but nothing in the pipeline enforces it. These tests verify that:
 *   (1) out-of-range / NaN / infinite score & confidence are clamped/defaulted into [0,1];
 *   (2) a delta with null/blank valueJson is skipped entirely (no insert/update,
 *       no exception) so it can't kill the whole async batch via a NOT NULL violation.
 */
@ExtendWith(MockitoExtension.class)
class UserPortraitServiceApplyDeltasTest {

    @Mock private UserPortraitMapper mapper;
    @Mock private UserPortraitHistoryMapper historyMapper;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UserPortraitService service;

    private static final Long USER_ID = 1L;

    @Test
    @DisplayName("IC-DATA-003: out-of-range / negative score & confidence are clamped into [0,1] on insert")
    void outOfRangeScoreAndConfidence_clampedToUnitInterval() {
        // No existing row -> insert path
        when(mapper.selectOne(any())).thenReturn(null);

        PortraitDeltas.Delta bad = new PortraitDeltas.Delta(
                "values", "{\"v\":\"growth\"}", 5.0, -1.0, null);

        service.applyDeltas(USER_ID, List.of(bad));

        ArgumentCaptor<UserPortrait> captor = ArgumentCaptor.forClass(UserPortrait.class);
        verify(mapper, times(1)).insert(captor.capture());
        verify(mapper, never()).updateById(any(UserPortrait.class));

        UserPortrait persisted = captor.getValue();
        assertEquals(1.0, persisted.score, 1e-9, "score=5.0 should clamp to 1.0");
        assertEquals(0.0, persisted.confidence, 1e-9, "confidence=-1.0 should clamp to 0.0");
    }

    @Test
    @DisplayName("IC-DATA-003: NaN / infinite score & confidence fall back to 0.0 safe default")
    void nanAndInfiniteValues_fallBackToZero() {
        when(mapper.selectOne(any())).thenReturn(null);

        PortraitDeltas.Delta nan = new PortraitDeltas.Delta(
                "tone", "{\"v\":\"warm\"}", Double.NaN, Double.POSITIVE_INFINITY, null);

        service.applyDeltas(USER_ID, List.of(nan));

        ArgumentCaptor<UserPortrait> captor = ArgumentCaptor.forClass(UserPortrait.class);
        verify(mapper, times(1)).insert(captor.capture());

        UserPortrait persisted = captor.getValue();
        assertEquals(0.0, persisted.score, 1e-9, "NaN score should default to 0.0");
        assertEquals(0.0, persisted.confidence, 1e-9, "+Inf confidence should fall back to 0.0 safe default");
    }

    @Test
    @DisplayName("IC-DATA-003: in-range values pass through unchanged")
    void inRangeValues_passThrough() {
        when(mapper.selectOne(any())).thenReturn(null);

        PortraitDeltas.Delta ok = new PortraitDeltas.Delta(
                "values", "{\"v\":\"calm\"}", 0.42, 0.73, null);

        service.applyDeltas(USER_ID, List.of(ok));

        ArgumentCaptor<UserPortrait> captor = ArgumentCaptor.forClass(UserPortrait.class);
        verify(mapper, times(1)).insert(captor.capture());

        UserPortrait persisted = captor.getValue();
        assertEquals(0.42, persisted.score, 1e-9);
        assertEquals(0.73, persisted.confidence, 1e-9);
    }

    @Test
    @DisplayName("IC-DATA-003: delta with null valueJson is skipped — no insert/update, no exception")
    void nullValueJson_isSkippedWithoutPersistOrException() {
        PortraitDeltas.Delta nullValue = new PortraitDeltas.Delta(
                "values", null, 0.5, 0.5, null);

        // Must not throw
        assertDoesNotThrow(() -> service.applyDeltas(USER_ID, List.of(nullValue)));

        // No DB writes for the skipped delta
        verify(mapper, never()).insert(any(UserPortrait.class));
        verify(mapper, never()).updateById(any(UserPortrait.class));
        // get()/selectOne should not even be reached for a skipped delta
        verify(mapper, never()).selectOne(any());
        verify(historyMapper, never()).insert(any(UserPortraitHistory.class));
    }

    @Test
    @DisplayName("IC-DATA-003: delta with blank valueJson is skipped — no insert/update")
    void blankValueJson_isSkipped() {
        PortraitDeltas.Delta blankValue = new PortraitDeltas.Delta(
                "values", "   ", 0.5, 0.5, null);

        assertDoesNotThrow(() -> service.applyDeltas(USER_ID, List.of(blankValue)));

        verify(mapper, never()).insert(any(UserPortrait.class));
        verify(mapper, never()).updateById(any(UserPortrait.class));
    }

    @Test
    @DisplayName("IC-DATA-003: a blank delta in a batch is skipped while a valid delta still persists")
    void blankDeltaSkipped_validDeltaInSameBatchStillPersists() {
        when(mapper.selectOne(any())).thenReturn(null);

        PortraitDeltas.Delta blank = new PortraitDeltas.Delta(
                "tone", "", 0.9, 0.9, null);
        PortraitDeltas.Delta good = new PortraitDeltas.Delta(
                "values", "{\"v\":\"open\"}", 0.6, 0.8, null);

        service.applyDeltas(USER_ID, List.of(blank, good));

        // Only the good delta is inserted
        ArgumentCaptor<UserPortrait> captor = ArgumentCaptor.forClass(UserPortrait.class);
        verify(mapper, times(1)).insert(captor.capture());
        assertEquals("values", captor.getValue().dim);
        assertEquals(0.6, captor.getValue().score, 1e-9);
    }
}
