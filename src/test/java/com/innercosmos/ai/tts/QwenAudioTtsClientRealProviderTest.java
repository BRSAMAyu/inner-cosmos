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
 * W1 / INNO-INNER-013 — proves the actual committed {@link QwenAudioTtsClient} (not just the
 * throwaway Node spike script) synthesizes real audio over the real network, for every voice
 * preset the product ships.
 *
 * <p>Tagged {@code real-provider} and excluded from the default {@code ./mvnw test} gate (see
 * {@code pom.xml}'s {@code excludedGroups}), matching the {@code MemoryEmbeddingRealProviderIndexRetrievalTest}
 * / {@code TrackARealProviderSmokeEvaluationTest} convention. Reads {@code TTS_API_KEY} (and
 * optionally {@code TTS_WS_URL}) only from the process environment; self-skips to a
 * {@code SKIPPED_NO_CREDENTIAL} evidence row (never a silent pass) when absent. Run explicitly:
 * <pre>
 *   export TTS_API_KEY=sk-...
 *   export TTS_WS_URL=wss://&lt;your-gateway&gt;/api-ws/v1/inference   # optional; defaults to the public gateway
 *   ./mvnw test -Dtest=QwenAudioTtsClientRealProviderTest -DexcludedGroups=
 * </pre>
 */
@Tag("real-provider")
class QwenAudioTtsClientRealProviderTest {

    @Test
    void everyPresetVoiceSynthesizesRealNonEmptyAudio() throws Exception {
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
        for (TtsVoicePreset preset : TtsVoicePresets.ALL) {
            Map<String, Object> row = new LinkedHashMap<>();
            try {
                byte[] audio = client.synthesize(preset.previewText(), preset.id());
                row.put("status", "OK");
                row.put("audioBytes", audio.length);
                row.put("model", preset.model());
                row.put("providerVoice", preset.providerVoice());
            } catch (Exception failure) {
                row.put("status", "FAILED: " + failure.getClass().getSimpleName());
                row.put("message", failure.getMessage());
            }
            perVoice.put(preset.id(), row);
        }
        report.put("status", "CALLED");
        report.put("wsUrl", wsUrl);
        report.put("voices", perVoice);
        writeReport(objectMapper, report);

        for (TtsVoicePreset preset : TtsVoicePresets.ALL) {
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) perVoice.get(preset.id());
            assertTrue("OK".equals(row.get("status")) && ((Integer) row.getOrDefault("audioBytes", 0)) > 0,
                    "voice " + preset.id() + " must synthesize non-empty real audio: " + row);
        }
    }

    private void writeReport(ObjectMapper objectMapper, Map<String, Object> report) throws Exception {
        Path output = Path.of("target", "evaluation", "tts-real-provider-report.json");
        Files.createDirectories(output.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
    }
}
