package com.innercosmos.asr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

public class MimoAsrClient implements AsrClient {

    private static final Logger log = LoggerFactory.getLogger(MimoAsrClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String DEFAULT_BASE_URL = "https://token-plan-cn.xiaomimimo.com/v1";
    private static final String DEFAULT_MODEL = "mimo-v2.5-asr";
    private static final int DEFAULT_TIMEOUT_MS = 30_000;

    private final String apiKey;
    private final String endpoint;
    private final String model;
    private final String language;
    private final int timeoutMs;
    private final MockAsrClient fallback = new MockAsrClient();

    public MimoAsrClient(String apiKey, String baseUrl, String model, String language, int timeoutMs) {
        this.apiKey = apiKey;
        this.endpoint = normalizeEndpoint(baseUrl);
        this.model = (model == null || model.isBlank()) ? DEFAULT_MODEL : model;
        this.language = (language == null || language.isBlank()) ? "auto" : language;
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
    }

    @Override
    public AsrResult transcribe(byte[] audioBytes, String hintText) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("MiMo ASR API key is empty, falling back to mock");
            return fallback.transcribe(audioBytes, hintText);
        }

        try {
            return doTranscribe(audioBytes);
        } catch (Exception firstError) {
            log.warn("MiMo ASR failed on first attempt, retrying once: {}", firstError.getMessage());
            try {
                return doTranscribe(audioBytes);
            } catch (Exception retryError) {
                log.error("MiMo ASR failed on retry, falling back to mock: {}", retryError.getMessage());
                return fallback.transcribe(audioBytes, hintText);
            }
        }
    }

    private AsrResult doTranscribe(byte[] audioBytes) throws Exception {
        String mimeType = detectSupportedMimeType(audioBytes);
        String dataUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(audioBytes);

        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("stream", false);

        ArrayNode messages = root.putArray("messages");
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        ArrayNode content = user.putArray("content");
        ObjectNode audioPart = content.addObject();
        audioPart.put("type", "input_audio");
        ObjectNode inputAudio = audioPart.putObject("input_audio");
        inputAudio.put("data", dataUrl);

        ObjectNode asrOptions = root.putObject("asr_options");
        asrOptions.put("language", language);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("api-key", apiKey)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMillis(timeoutMs))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(root), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("MiMo ASR API returned status " + response.statusCode() + ": " + response.body());
        }

        JsonNode responseRoot = objectMapper.readTree(response.body());
        JsonNode contentNode = responseRoot.path("choices").path(0).path("message").path("content");
        if (!contentNode.isTextual() || contentNode.asText().isBlank()) {
            throw new RuntimeException("Unexpected MiMo ASR response format: " + response.body());
        }

        AsrResult result = new AsrResult();
        result.text = contentNode.asText().trim();
        result.audioDurationSec = responseRoot.path("usage").path("seconds").asInt(Math.max(3, result.text.length() / 3));
        result.speechRate = Math.max(1.0, result.text.length() / (double) Math.max(1, result.audioDurationSec));
        result.pauseCount = result.text.contains(",") || result.text.contains("，") ? 2 : 1;
        result.longPauseCount = 0;
        result.inputConfidence = 0.94;
        return result;
    }

    private String detectSupportedMimeType(byte[] audioBytes) {
        if (audioBytes == null || audioBytes.length < 12) {
            throw new IllegalArgumentException("Audio file is empty or too small");
        }
        if (audioBytes[0] == 'R' && audioBytes[1] == 'I' && audioBytes[2] == 'F' && audioBytes[3] == 'F'
                && audioBytes[8] == 'W' && audioBytes[9] == 'A' && audioBytes[10] == 'V' && audioBytes[11] == 'E') {
            return "audio/wav";
        }
        if ((audioBytes[0] == 'I' && audioBytes[1] == 'D' && audioBytes[2] == '3')
                || ((audioBytes[0] & 0xFF) == 0xFF && ((audioBytes[1] & 0xE0) == 0xE0))) {
            return "audio/mpeg";
        }
        throw new IllegalArgumentException("MiMo ASR only accepts mp3 or wav audio");
    }

    private String normalizeEndpoint(String baseUrl) {
        String url = (baseUrl == null || baseUrl.isBlank()) ? DEFAULT_BASE_URL : baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/chat/completions")) {
            return url;
        }
        return url + "/chat/completions";
    }
}
