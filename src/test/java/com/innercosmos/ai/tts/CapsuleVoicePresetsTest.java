package com.innercosmos.ai.tts;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Capsule persona voice catalog: distinct, capsule-scoped voice presets used when a visitor hears
 * a capsule reply spoken aloud. Pinned separately from Aurora's {@link TtsVoicePresets} so the two
 * stay disjoint (a capsule voice must never appear in the Aurora settings picker, and vice versa).
 */
class CapsuleVoicePresetsTest {

    @Test
    void catalogHasAtLeastTwoCapsuleVoices() {
        assertTrue(CapsuleVoicePresets.ALL.size() >= 2, "expected at least 2 capsule persona voices");
        for (TtsVoicePreset preset : CapsuleVoicePresets.ALL) {
            assertNotNull(preset.label(), "label must not be null");
            assertEquals("zh", preset.language(), "capsule voices are Chinese-capable");
            assertNotNull(preset.providerVoice(), "providerVoice must not be null");
            assertNotNull(preset.model(), "model must not be null");
        }
    }

    @Test
    void capsuleVoiceIdsArePrefixedAndDistinctFromEachOther() {
        for (TtsVoicePreset preset : CapsuleVoicePresets.ALL) {
            assertTrue(preset.id().startsWith("capsule_"),
                "capsule voice id must be capsule-scoped: " + preset.id());
        }
        long distinct = CapsuleVoicePresets.ALL.stream().map(TtsVoicePreset::id).distinct().count();
        assertEquals(CapsuleVoicePresets.ALL.size(), distinct, "capsule voice ids must be unique");
    }

    @Test
    void capsuleVoicesAreDisjointFromAuroraInnerVoiceCatalog() {
        // The whole point of a separate catalog: capsule persona voices must NOT collide with
        // Aurora's inner-voice presets, so resolving an id can never silently cross the streams.
        for (TtsVoicePreset capsule : CapsuleVoicePresets.ALL) {
            assertTrue(TtsVoicePresets.byId(capsule.id()).isEmpty(),
                "capsule voice id must not exist in Aurora's catalog: " + capsule.id());
        }
        for (TtsVoicePreset aurora : TtsVoicePresets.ALL) {
            assertTrue(CapsuleVoicePresets.byId(aurora.id()).isEmpty(),
                "Aurora voice id must not exist in capsule catalog: " + aurora.id());
        }
    }

    @Test
    void capsuleVoicesUseProviderPairsNotAlreadyInAuroraCatalog() {
        // Reinforces "distinct persona": the vendor (model, voice) pairs shipped for capsules are
        // not the same ones Aurora already uses.
        for (TtsVoicePreset capsule : CapsuleVoicePresets.ALL) {
            for (TtsVoicePreset aurora : TtsVoicePresets.ALL) {
                assertFalse(capsule.providerVoice().equals(aurora.providerVoice()),
                    "capsule voice " + capsule.id() + " reuses Aurora providerVoice " + capsule.providerVoice());
            }
        }
    }

    @Test
    void byIdResolvesCapsuleVoicesAndRejectsUnknown() {
        assertTrue(CapsuleVoicePresets.byId(null).isEmpty());
        assertTrue(CapsuleVoicePresets.byId("does-not-exist").isEmpty());
        assertEquals(CapsuleVoicePresets.ALL.get(0).id(),
            CapsuleVoicePresets.byId(CapsuleVoicePresets.ALL.get(0).id()).orElseThrow().id());
    }

    @Test
    void defaultVoiceIsFirstAndAlwaysPresent() {
        assertNotNull(CapsuleVoicePresets.defaultVoice());
        assertEquals(CapsuleVoicePresets.ALL.get(0).id(), CapsuleVoicePresets.defaultVoice().id());
    }
}
