package com.innercosmos.service.impl;

import com.innercosmos.ai.agent.LetterGuardAgent;
import com.innercosmos.ai.tts.TtsClient;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * W1 slow-letter voice reuse: unit-coverage for {@link SlowLetterServiceImpl#synthesizeVoice}. Pins
 * every authorization gate (recipient-only + the reused delivered-to-recipient delivery-state gate)
 * and the Aurora-style omit-on-failure resilience without a network: the {@link TtsClient} is a stub,
 * so we assert WHICH bytes/voice are synthesized and that a synthesis failure never escapes as a raw
 * exception (it becomes a clean {@code AI_PROVIDER_ERROR}). Mirrors the capsule-voice test contract.
 */
@ExtendWith(MockitoExtension.class)
class SlowLetterServiceImplSynthesizeVoiceTest {

    @Mock private SlowLetterMapper letterMapper;
    @Mock private LetterStatusLogMapper logMapper;
    @Mock private LetterStateRegistry stateRegistry;
    @Mock private LetterGuardAgent guardAgent;
    @Mock private LetterThreadMapper threadMapper;
    @Mock private ReportRecordMapper reportRecordMapper;
    @Mock private LetterSafetyFilter letterSafetyFilter;
    @Mock private EchoCapsuleMapper capsuleMapper;
    @Mock private BlockRelationMapper blockRelationMapper;
    @Mock private TtsClient ttsClient;

    private static final Long RECEIVER_ID = 101L;
    private static final Long SENDER_ID = 202L;
    private static final Long LETTER_ID = 7L;
    private static final String LETTER_BODY = "我在第三段停了下来，想慢慢回你一封信。";

    private SlowLetterServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SlowLetterServiceImpl(letterMapper, logMapper, stateRegistry,
                guardAgent, threadMapper, reportRecordMapper, letterSafetyFilter, capsuleMapper,
                blockRelationMapper, new PiiCredentialDetector(), Clock.systemUTC());
        ReflectionTestUtils.setField(service, "ttsClient", ttsClient);
    }

    @Test
    @DisplayName("rejects the sender (only the recipient may hear a letter read aloud)")
    void rejectsSender() {
        SlowLetter letter = letter("DELIVERED");
        when(letterMapper.selectById(LETTER_ID)).thenReturn(letter);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.synthesizeVoice(SENDER_ID, LETTER_ID));
        assertEquals("UNAUTHORIZED", ex.code);
        verifyNoInteractions(ttsClient);
    }

    @Test
    @DisplayName("rejects a third party who is neither sender nor recipient (IDOR guard)")
    void rejectsThirdParty() {
        SlowLetter letter = letter("DELIVERED");
        when(letterMapper.selectById(LETTER_ID)).thenReturn(letter);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.synthesizeVoice(999L, LETTER_ID));
        assertEquals("UNAUTHORIZED", ex.code);
        verifyNoInteractions(ttsClient);
    }

    @Test
    @DisplayName("rejects the recipient while the letter is still FLYING in transit (delivery-state gate)")
    void rejectsInFlightLetter() {
        SlowLetter letter = letter("FLYING");
        when(letterMapper.selectById(LETTER_ID)).thenReturn(letter);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.synthesizeVoice(RECEIVER_ID, LETTER_ID));
        assertEquals("LETTER_STATE_INVALID", ex.code);
        verifyNoInteractions(ttsClient);
    }

    @Test
    @DisplayName("rejects the recipient for an unsent DRAFT (delivery-state gate)")
    void rejectsDraftLetter() {
        SlowLetter letter = letter("DRAFT");
        when(letterMapper.selectById(LETTER_ID)).thenReturn(letter);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.synthesizeVoice(RECEIVER_ID, LETTER_ID));
        assertEquals("LETTER_STATE_INVALID", ex.code);
        verifyNoInteractions(ttsClient);
    }

    @Test
    @DisplayName("rejects when the letter does not exist")
    void rejectsMissingLetter() {
        when(letterMapper.selectById(LETTER_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.synthesizeVoice(RECEIVER_ID, LETTER_ID));
        assertEquals("NOT_FOUND", ex.code);
        verifyNoInteractions(ttsClient);
    }

    @Test
    @DisplayName("returns AI_PROVIDER_ERROR when TTS is not configured (clean, never a 500)")
    void unavailableProviderIsCleanError() {
        SlowLetter letter = letter("DELIVERED");
        when(letterMapper.selectById(LETTER_ID)).thenReturn(letter);
        when(ttsClient.available()).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.synthesizeVoice(RECEIVER_ID, LETTER_ID));
        assertEquals("AI_PROVIDER_ERROR", ex.code);
        verify(ttsClient, never()).synthesize(anyString(), anyString());
    }

    @Test
    @DisplayName("synthesizes the delivered letter body in the warm default Aurora voice")
    void synthesizesBodyInWarmVoice() {
        SlowLetter letter = letter("READ");
        when(letterMapper.selectById(LETTER_ID)).thenReturn(letter);
        when(ttsClient.available()).thenReturn(true);
        byte[] audio = new byte[]{1, 2, 3, 4};
        when(ttsClient.synthesize(eq(LETTER_BODY), eq("warm_gentle_female"))).thenReturn(audio);

        byte[] result = service.synthesizeVoice(RECEIVER_ID, LETTER_ID);

        assertSame(audio, result);
        // Proves the warm Aurora default voice was selected (reused from TtsVoicePresets, no new catalog).
        verify(ttsClient).synthesize(LETTER_BODY, "warm_gentle_female");
    }

    @Test
    @DisplayName("a synthesis failure/timeout becomes AI_PROVIDER_ERROR, never a raw exception")
    void synthesisFailureIsCleanError() {
        SlowLetter letter = letter("DELIVERED");
        when(letterMapper.selectById(LETTER_ID)).thenReturn(letter);
        when(ttsClient.available()).thenReturn(true);
        // Mirrors the real QwenAudioTtsClient timeout path: IllegalStateException("tts synthesis timed out...").
        when(ttsClient.synthesize(anyString(), anyString()))
                .thenThrow(new IllegalStateException("tts synthesis timed out after 8000ms"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.synthesizeVoice(RECEIVER_ID, LETTER_ID));
        assertEquals("AI_PROVIDER_ERROR", ex.code);
    }

    @Test
    @DisplayName("still allows hearing a READ letter the recipient has already engaged with")
    void allowsReadLetter() {
        SlowLetter letter = letter("READ");
        when(letterMapper.selectById(LETTER_ID)).thenReturn(letter);
        when(ttsClient.available()).thenReturn(true);
        when(ttsClient.synthesize(eq(LETTER_BODY), eq("warm_gentle_female"))).thenReturn(new byte[]{9});

        byte[] result = service.synthesizeVoice(RECEIVER_ID, LETTER_ID);
        assertEquals(1, result.length);
    }

    // ---------- fixtures ----------

    /** A letter from SENDER to RECEIVER with the given status. */
    private SlowLetter letter(String status) {
        SlowLetter letter = new SlowLetter();
        letter.id = LETTER_ID;
        letter.senderUserId = SENDER_ID;
        letter.receiverUserId = RECEIVER_ID;
        letter.title = "一封来自远方的信";
        letter.letterBody = LETTER_BODY;
        letter.status = status;
        return letter;
    }
}
