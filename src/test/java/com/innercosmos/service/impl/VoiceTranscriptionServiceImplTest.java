package com.innercosmos.service.impl;

import com.innercosmos.asr.AsrResult;
import com.innercosmos.entity.VoiceTranscription;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.VoiceTranscriptionMapper;
import com.innercosmos.service.MemorySettlementService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * G2.ARCH-MODULES: DiaryController now goes through VoiceTranscriptionService instead of
 * VoiceTranscriptionMapper directly. This pins the owner-scope check (M-030-style: a transcription
 * only belongs to the user who created it) and the submit-final write+settle sequence at the
 * service layer.
 */
class VoiceTranscriptionServiceImplTest {

    private static AsrResult asrResult() {
        AsrResult asr = new AsrResult();
        asr.audioDurationSec = 12;
        asr.speechRate = 3.5;
        asr.pauseCount = 2;
        return asr;
    }

    @Test
    void createPersistsARawTranscriptionOwnedByTheCallingUser() {
        VoiceTranscriptionMapper mapper = mock(VoiceTranscriptionMapper.class);
        var service = new VoiceTranscriptionServiceImpl(mapper, mock(MemorySettlementService.class));

        VoiceTranscription created = service.create(7L, "今天天气不错", asrResult(), "DIARY");

        assertEquals(7L, created.userId);
        assertEquals("今天天气不错", created.originalText);
        assertEquals("今天天气不错", created.editedText);
        assertEquals("RAW", created.status);
        assertEquals("DIARY", created.emotionHint);
        verify(mapper).insert(created);
    }

    @Test
    void getOwnedReturnsTheRowWhenItBelongsToTheCaller() {
        VoiceTranscriptionMapper mapper = mock(VoiceTranscriptionMapper.class);
        VoiceTranscription row = new VoiceTranscription();
        row.userId = 7L;
        when(mapper.selectById(42L)).thenReturn(row);
        var service = new VoiceTranscriptionServiceImpl(mapper, mock(MemorySettlementService.class));

        assertEquals(row, service.getOwned(42L, 7L));
    }

    @Test
    void getOwnedRejectsARowThatDoesNotExist() {
        VoiceTranscriptionMapper mapper = mock(VoiceTranscriptionMapper.class);
        when(mapper.selectById(anyLong())).thenReturn(null);
        var service = new VoiceTranscriptionServiceImpl(mapper, mock(MemorySettlementService.class));

        BusinessException failure = assertThrows(BusinessException.class, () -> service.getOwned(42L, 7L));
        assertEquals("NOT_FOUND", failure.code);
    }

    @Test
    void getOwnedRejectsARowOwnedByAnotherUser() {
        VoiceTranscriptionMapper mapper = mock(VoiceTranscriptionMapper.class);
        VoiceTranscription row = new VoiceTranscription();
        row.userId = 999L;
        when(mapper.selectById(42L)).thenReturn(row);
        var service = new VoiceTranscriptionServiceImpl(mapper, mock(MemorySettlementService.class));

        BusinessException failure = assertThrows(BusinessException.class, () -> service.getOwned(42L, 7L));
        assertEquals("NOT_FOUND", failure.code);
    }

    @Test
    void submitFinalWritesEditedTextMarksSubmittedAndSettlesMemory() {
        VoiceTranscriptionMapper mapper = mock(VoiceTranscriptionMapper.class);
        VoiceTranscription row = new VoiceTranscription();
        row.userId = 7L;
        row.status = "RAW";
        when(mapper.selectById(42L)).thenReturn(row);
        MemorySettlementService settlement = mock(MemorySettlementService.class);
        var service = new VoiceTranscriptionServiceImpl(mapper, settlement);

        service.submitFinal(42L, 7L, "最终版日记内容");

        assertEquals("最终版日记内容", row.editedText);
        assertEquals("SUBMITTED", row.status);
        verify(mapper).updateById(row);
        verify(settlement).settleDiary(7L, "最终版日记内容");
    }

    @Test
    void submitFinalRejectsARowOwnedByAnotherUserAndNeverWrites() {
        VoiceTranscriptionMapper mapper = mock(VoiceTranscriptionMapper.class);
        VoiceTranscription row = new VoiceTranscription();
        row.userId = 999L;
        when(mapper.selectById(42L)).thenReturn(row);
        MemorySettlementService settlement = mock(MemorySettlementService.class);
        var service = new VoiceTranscriptionServiceImpl(mapper, settlement);

        assertThrows(BusinessException.class, () -> service.submitFinal(42L, 7L, "不该写入"));

        verify(mapper, never()).updateById(any(VoiceTranscription.class));
        verify(settlement, never()).settleDiary(anyLong(), eq("不该写入"));
    }
}
