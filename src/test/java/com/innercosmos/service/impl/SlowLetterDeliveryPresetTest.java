package com.innercosmos.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.innercosmos.ai.agent.LetterGuardAgent;
import com.innercosmos.dto.LetterCreateRequest;
import com.innercosmos.dto.LetterDeliveryPreset;
import com.innercosmos.entity.SlowLetter;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.letterstate.LetterStateRegistry;
import com.innercosmos.mapper.BlockRelationMapper;
import com.innercosmos.mapper.EchoCapsuleMapper;
import com.innercosmos.mapper.LetterStatusLogMapper;
import com.innercosmos.mapper.LetterThreadMapper;
import com.innercosmos.mapper.ReportRecordMapper;
import com.innercosmos.mapper.SlowLetterMapper;
import com.innercosmos.safety.PiiCredentialDetector;
import com.innercosmos.service.LetterSafetyFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlowLetterDeliveryPresetTest {

    @Mock private SlowLetterMapper letterMapper;
    @Mock private LetterStatusLogMapper logMapper;
    @Mock private LetterStateRegistry stateRegistry;
    @Mock private LetterGuardAgent guardAgent;
    @Mock private LetterThreadMapper threadMapper;
    @Mock private ReportRecordMapper reportRecordMapper;
    @Mock private LetterSafetyFilter letterSafetyFilter;
    @Mock private EchoCapsuleMapper capsuleMapper;
    @Mock private BlockRelationMapper blockRelationMapper;

    private static final Instant NOW = Instant.parse("2026-07-26T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private SlowLetterServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SlowLetterServiceImpl(letterMapper, logMapper, stateRegistry, guardAgent,
                threadMapper, reportRecordMapper, letterSafetyFilter, capsuleMapper,
                blockRelationMapper, new PiiCredentialDetector(), CLOCK);
    }

    @Test
    void demoThirtySecondsStartsAtSendNotDraftCreation() {
        when(guardAgent.allow(any())).thenReturn(true);
        LetterCreateRequest request = request(LetterDeliveryPreset.DEMO_30S);

        SlowLetter draft = service.draft(1L, request);

        assertEquals("DEMO_30S", draft.deliveryPreset);
        assertNull(draft.estimatedArrivalAt);
        assertNull(draft.scheduledArrivalAt);

        draft.id = 41L;
        when(letterMapper.selectById(41L)).thenReturn(draft);
        when(letterMapper.update(any(), any())).thenReturn(1);
        allowDelivery();

        SlowLetter sent = service.transition(1L, 41L, "SENT");

        LocalDateTime expected = LocalDateTime.ofInstant(NOW.plusSeconds(30), ZoneOffset.UTC);
        assertEquals(expected, sent.scheduledArrivalAt);
        assertEquals(expected, sent.estimatedArrivalAt);
        assertEquals(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), sent.sentAt);
    }

    @Test
    void tonightUsesTheRequestedIanaZoneAndPersistsUtc() {
        SlowLetter draft = draft("TONIGHT");
        draft.deliveryTimeZone = "Asia/Shanghai";
        when(letterMapper.selectById(42L)).thenReturn(draft);
        when(letterMapper.update(any(), any())).thenReturn(1);
        allowDelivery();

        SlowLetter sent = service.transition(1L, 42L, "SENT");

        // 10:00Z is 18:00 in Shanghai, so "tonight" is 21:00 local / 13:00Z.
        LocalDateTime expected = LocalDateTime.of(2026, 7, 26, 13, 0);
        assertEquals(expected, sent.estimatedArrivalAt);
        assertEquals("Asia/Shanghai", sent.deliveryTimeZone);
    }

    @Test
    void tomorrowMeansTheSameLocalTimeOnTheNextDay() {
        SlowLetter draft = draft("TOMORROW");
        draft.deliveryTimeZone = "Asia/Shanghai";
        when(letterMapper.selectById(42L)).thenReturn(draft);
        when(letterMapper.update(any(), any())).thenReturn(1);
        allowDelivery();

        SlowLetter sent = service.transition(1L, 42L, "SENT");

        assertEquals(LocalDateTime.of(2026, 7, 27, 10, 0), sent.estimatedArrivalAt);
    }

    @Test
    void customArrivalMustStillBeInTheFutureWhenTheDraftIsSent() {
        SlowLetter draft = draft("CUSTOM");
        draft.scheduledArrivalAt = LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC);
        when(letterMapper.selectById(42L)).thenReturn(draft);
        allowDelivery();

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.transition(1L, 42L, "SENT"));

        assertEquals("BAD_REQUEST", error.code);
        verify(letterMapper, never()).update(any(), any());
    }

    @Test
    void customIntentIsValidatedAtDraftCreation() {
        when(guardAgent.allow(any())).thenReturn(true);
        LetterCreateRequest request = request(LetterDeliveryPreset.CUSTOM);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.draft(1L, request));

        assertEquals("BAD_REQUEST", error.code);
        verify(letterMapper, never()).insert(any(SlowLetter.class));
    }

    @Test
    void wireTimestampsCarryAnExplicitUtcOffset() throws Exception {
        SlowLetter letter = draft("DEMO_30S");
        letter.scheduledArrivalAt = LocalDateTime.of(2026, 7, 26, 13, 0);
        letter.estimatedArrivalAt = letter.scheduledArrivalAt;
        letter.sentAt = LocalDateTime.of(2026, 7, 26, 12, 59, 30);
        letter.deliveredAt = LocalDateTime.of(2026, 7, 26, 13, 0, 5);

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String json = mapper.writeValueAsString(letter);

        assertTrue(json.contains("\"scheduledArrivalAt\":\"2026-07-26T13:00:00Z\""));
        assertTrue(json.contains("\"estimatedArrivalAt\":\"2026-07-26T13:00:00Z\""));
        assertTrue(json.contains("\"sentAt\":\"2026-07-26T12:59:30Z\""));
        assertTrue(json.contains("\"deliveredAt\":\"2026-07-26T13:00:05Z\""));
    }

    private LetterCreateRequest request(LetterDeliveryPreset preset) {
        LetterCreateRequest request = new LetterCreateRequest();
        request.receiverUserId = 2L;
        request.title = "给明天的你";
        request.letterBody = "愿这封信在合适的时候抵达。";
        request.deliveryPreset = preset;
        return request;
    }

    private SlowLetter draft(String preset) {
        SlowLetter letter = new SlowLetter();
        letter.id = 42L;
        letter.senderUserId = 1L;
        letter.receiverUserId = 2L;
        letter.title = "一封慢信";
        letter.letterBody = "慢慢说，也认真抵达。";
        letter.status = "DRAFT";
        letter.deliveryPreset = preset;
        return letter;
    }

    private void allowDelivery() {
        LetterSafetyFilter.FilterResult result = new LetterSafetyFilter.FilterResult();
        result.passed = true;
        when(letterSafetyFilter.filter(any(), any(), any())).thenReturn(result);
    }
}
