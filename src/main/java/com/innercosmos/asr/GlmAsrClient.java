package com.innercosmos.asr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class GlmAsrClient implements AsrClient {

    private static final Logger log = LoggerFactory.getLogger(GlmAsrClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String DEFAULT_ASR_URL = "https://open.bigmodel.cn/api/paas/v4/audio/transcriptions";
    private static final int TIMEOUT_MS = 30_000;

    private final String apiKey;
    private final String asrUrl;
    private final MockAsrClient fallback = new MockAsrClient();

    public GlmAsrClient(String apiKey) {
        this(apiKey, DEFAULT_ASR_URL);
    }

    public GlmAsrClient(String apiKey, String asrUrl) {
        this.apiKey = apiKey;
        this.asrUrl = asrUrl;
    }

    @Override
    public AsrResult transcribe(byte[] audioBytes, String hintText) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GLM ASR API key is empty, falling back to mock");
            return fallback.transcribe(audioBytes, hintText);
        }

        try {
            return doTranscribe(audioBytes);
        } catch (Exception firstError) {
            log.warn("GLM ASR failed on first attempt, retrying once: {}", firstError.getMessage());
            try {
                return doTranscribe(audioBytes);
            } catch (Exception retryError) {
                log.error("GLM ASR failed on retry, falling back to mock: {}", retryError.getMessage());
                return fallback.transcribe(audioBytes, hintText);
            }
        }
    }

    private AsrResult doTranscribe(byte[] audioBytes) throws Exception {
        String boundary = "----InnerCosmosBoundary" + System.currentTimeMillis();

        String audioPartHeader = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n"
                + "Content-Type: audio/wav\r\n\r\n";

        String modelPart = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"model\"\r\n\r\n"
                + "whisper-1\r\n";

        String closingBoundary = "--" + boundary + "--\r\n";

        byte[] headerBytes = audioPartHeader.getBytes("UTF-8");
        byte[] modelPartBytes = modelPart.getBytes("UTF-8");
        byte[] closingBytes = closingBoundary.getBytes("UTF-8");

        byte[] multipartBody = new byte[headerBytes.length + audioBytes.length
                + modelPartBytes.length + closingBytes.length];
        System.arraycopy(headerBytes, 0, multipartBody, 0, headerBytes.length);
        System.arraycopy(audioBytes, 0, multipartBody, headerBytes.length, audioBytes.length);
        System.arraycopy(modelPartBytes, 0, multipartBody, headerBytes.length + audioBytes.length, modelPartBytes.length);
        System.arraycopy(closingBytes, 0, multipartBody,
                headerBytes.length + audioBytes.length + modelPartBytes.length, closingBytes.length);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(TIMEOUT_MS))
                .build();

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(asrUrl))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(Duration.ofMillis(TIMEOUT_MS))
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                .build();

        HttpResponse<String> httpResponse = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (httpResponse.statusCode() != 200) {
            throw new RuntimeException("GLM ASR API returned status " + httpResponse.statusCode() + ": " + httpResponse.body());
        }

        AsrResult result = new AsrResult();
        JsonNode root = objectMapper.readTree(httpResponse.body());
        if (root.has("text")) {
            result.text = root.get("text").asText();
        } else {
            throw new RuntimeException("Unexpected GLM ASR response format: " + httpResponse.body());
        }
        result.audioDurationSec = Math.max(3, result.text.length() / 3);
        result.speechRate = Math.max(1.0, result.text.length() / (double) result.audioDurationSec);
        result.pauseCount = result.text.contains("，") ? 2 : 1;
        result.longPauseCount = 0;
        result.inputConfidence = 0.92;
        return result;
    }
}
