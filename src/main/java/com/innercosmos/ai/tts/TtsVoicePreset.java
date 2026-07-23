package com.innercosmos.ai.tts;

/**
 * One preset, synthesizable voice. {@code id}/{@code label}/{@code language}/{@code previewText}
 * are the public, frontend-facing fields (see {@code GET /api/me/tts/voices}); {@code model} and
 * {@code providerVoice} are the vendor-specific identifiers {@link TtsClient} implementations use
 * internally to synthesize -- never exposed over the REST contract.
 */
public record TtsVoicePreset(String id, String label, String language, String previewText,
                             String model, String providerVoice) {
}
