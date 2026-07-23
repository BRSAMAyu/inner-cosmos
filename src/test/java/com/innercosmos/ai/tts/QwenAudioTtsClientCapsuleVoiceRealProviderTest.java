package com.innercosmos.ai.tts;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W1 capsule-voice reuse: proves the actual committed {@link QwenAudioTtsClient} synthesizes real,
 * non-empty audio for the CAPSULE persona-voice presets ({@link CapsuleVoicePresets}) over the real
 * network -- i.e. the broadened {@code resolvePreset} path (Aurora + capsule catalogs) works against
 * the real provider, and the chosen vendor pairs for the distinct capsule persona voice authenticate.
 *
 * <p>Tagged {@code real-provider} and excluded from the default {@code ./mvnw test} gate (see
 * {@code pom.xml}'s {@code excludedGroups}), matching {@code QwenAudioTtsClientRealProviderTest}.
 * Reads {@code TTS_API_KEY} (and optionally {@code TTS_WS_URL}) only from the process environment;
 * self-skips to a {@code SKIPPED_NO_CREDENTIAL} evidence row (never a silent pass) when absent. Run:
 * <pre>
 *   export TTS_API_KEY=sk-...
 *   export TTS_WS_URL=wss://&lt;your-gateway&gt;/api-ws/v1/inference   # optional; defaults to the public gateway
 *   ./mvnw test -Dtest=QwenAudioTtsClientCapsuleVoiceRealProviderTest -DexcludedGroups=
 * </pre>
 */
@Tag("real-provider")
class QwenAudioTtsClientCapsuleVoiceRealProviderTest {

    @Test
    void everyCapsulePresetVoiceSynthesizesRealNonEmptyAudio() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        String apiKey = System.getenv("TTS_API_KEY");
        Map<String, Object> report = new LinkedHashMap<>();
        if (apiKey == null || apiKey.isBlank()) {
            report.put("status", "SKIPPED_NO_CREDENTIAL");
            report.put("note", "TTS_API_KEY not set in this session's environment; never falls back "
                    + "to a fake client silently");
            writeReport(objectMapper, report);
            return;
        }
        String wsUrl = System.getenv().getOrDefault("TTS_WS_URL",
                "wss://dashscope.aliyuncs.com/api-ws/v1/inference");
        QwenAudioTtsClient client = new QwenAudioTtsClient(apiKey, wsUrl, 15_000L, objectMapper);

        Map<String, Object> perVoice = new LinkedHashMap<>();
        for (TtsVoicePreset preset : CapsuleVoicePresets.ALL) {
            Map<String, Object> row = new LinkedHashMap<>();
            try {
                byte[] audio = client.synthesize(preset.previewText(), preset.id());
                row.put("status", "OK");
                row.put("audioBytes", audio.length);
                row.put("model", preset.model());
                row.put("providerVoice", preset.providerVoice());
            } catch (Exception failure) {
                // Honest disclosure: a transient per-voice timeout was observed during the W1
                // capsule-voice run too (see INNO-INNER-013 -- the failing voice differs across
                // runs and is Aliyun-side WebSocket latency, not a voice-specific defect). The
                // production code wraps this exact exception in try/catch and omits the audio.
                row.put("status", "FAILED: " + failure.getClass().getSimpleName());
                row.put("message", failure.getMessage());
            }
            perVoice.put(preset.id(), row);
        }
        report.put("status", "CALLED");
        report.put("wsUrl", wsUrl);
        report.put("voices", perVoice);
        writeReport(objectMapper, report);

        for (TtsVoicePreset preset : CapsuleVoicePresets.ALL) {
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) perVoice.get(preset.id());
            assertTrue("OK".equals(row.get("status")) && ((Integer) row.getOrDefault("audioBytes", 0)) > 0,
                    "capsule voice " + preset.id() + " must synthesize non-empty real audio: " + row);
        }
    }

    private void writeReport(ObjectMapper objectMapper, Map<String, Object> report) throws Exception {
        Path output = Path.of("target", "evaluation", "tts-capsule-voice-real-provider-report.json");
        Files.createDirectories(output.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
    }
}
