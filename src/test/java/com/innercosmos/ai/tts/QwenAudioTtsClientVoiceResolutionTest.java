package com.innercosmos.ai.tts;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the broadened voice-resolution contract on {@link QwenAudioTtsClient}: the SAME committed
 * client now resolves both Aurora inner-voice ids AND capsule persona-voice ids (so the capsule
 * voice path reuses this client rather than wrapping a second one), while still rejecting unknown
 * ids. Resolution is unit-tested directly so it does not need a live WebSocket to prove.
 */
class QwenAudioTtsClientVoiceResolutionTest {

    @Test
    void resolvesEveryAuroraInnerVoiceId() {
        for (TtsVoicePreset aurora : TtsVoicePresets.ALL) {
            TtsVoicePreset resolved = QwenAudioTtsClient.resolvePreset(aurora.id());
            assertSame(aurora, resolved, "Aurora voice id must resolve to its own preset: " + aurora.id());
        }
    }

    @Test
    void resolvesEveryCapsulePersonaVoiceId() {
        for (TtsVoicePreset capsule : CapsuleVoicePresets.ALL) {
            TtsVoicePreset resolved = QwenAudioTtsClient.resolvePreset(capsule.id());
            assertSame(capsule, resolved, "capsule voice id must resolve through the same client: " + capsule.id());
        }
    }

    @Test
    void unknownVoiceIdIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> QwenAudioTtsClient.resolvePreset("not-a-real-voice"));
        assertTrue(ex.getMessage().contains("not-a-real-voice"));
    }
}
