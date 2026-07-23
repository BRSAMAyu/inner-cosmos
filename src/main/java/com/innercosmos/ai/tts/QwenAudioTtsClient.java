package com.innercosmos.ai.tts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Real TTS synthesis against Aliyun DashScope's Qwen-Audio-TTS/CosyVoice family, which all share
 * one WebSocket protocol at {@code wss://.../api-ws/v1/inference}: a client sends a {@code
 * run-task} frame naming the vendor {@code model}/{@code voice}, waits for {@code task-started},
 * then sends {@code continue-task} (the text) followed by {@code finish-task}; the server streams
 * back binary audio frames and a final {@code task-finished}/{@code task-failed} text event.
 *
 * <p>Confirmed empirically during the W1 spike against the real account (see
 * {@code evidence/innovation/INNO-INNER-013/README.md}): {@code qwen-audio-3.0-tts-flash},
 * {@code cosyvoice-v2}, and {@code cosyvoice-v3-flash} all authenticate and synthesize over this
 * protocol with the same API key used for {@code text-embedding-v4}.
 */
public class QwenAudioTtsClient implements TtsClient {
    private static final Logger log = LoggerFactory.getLogger(QwenAudioTtsClient.class);

    private final String apiKey;
    private final String wsUrl;
    private final long timeoutMs;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public QwenAudioTtsClient(String apiKey, String wsUrl, long timeoutMs, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.wsUrl = wsUrl;
        this.timeoutMs = timeoutMs;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build();
    }

    @Override public boolean available() { return apiKey != null && !apiKey.isBlank(); }

    @Override public java.util.List<TtsVoicePreset> voices() { return TtsVoicePresets.ALL; }

    @Override
    public byte[] synthesize(String text, String voiceId) {
        if (!available()) throw new IllegalStateException("tts provider is not configured");
        TtsVoicePreset preset = TtsVoicePresets.byId(voiceId)
            .orElseThrow(() -> new IllegalArgumentException("unknown tts voice id: " + voiceId));
        if (text == null || text.isBlank()) throw new IllegalArgumentException("text to synthesize must not be blank");
        try {
            return synthesizeViaWebSocket(text, preset);
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("tts synthesis failed: " + e.getMessage(), e);
        }
    }

    private byte[] synthesizeViaWebSocket(String text, TtsVoicePreset preset) throws Exception {
        String taskId = UUID.randomUUID().toString();
        CompletableFuture<byte[]> resultFuture = new CompletableFuture<>();
        ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
        StringBuilder textFrame = new StringBuilder();

        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                textFrame.append(data);
                webSocket.request(1);
                if (last) {
                    String message = textFrame.toString();
                    textFrame.setLength(0);
                    handleServerEvent(webSocket, message);
                }
                return null;
            }

            @Override
            public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                byte[] chunk = new byte[data.remaining()];
                data.get(chunk);
                audioBuffer.write(chunk, 0, chunk.length);
                webSocket.request(1);
                return null;
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                resultFuture.completeExceptionally(error);
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                if (!resultFuture.isDone()) {
                    resultFuture.completeExceptionally(
                        new IllegalStateException("tts websocket closed before task-finished: " + statusCode + " " + reason));
                }
                return null;
            }

            private void handleServerEvent(WebSocket webSocket, String message) {
                try {
                    JsonNode root = objectMapper.readTree(message);
                    String event = root.path("header").path("event").asText("");
                    switch (event) {
                        case "task-started" -> {
                            webSocket.sendText(continueTaskJson(taskId, text), true);
                            webSocket.sendText(finishTaskJson(taskId), true);
                        }
                        case "task-finished" -> {
                            if (!resultFuture.isDone()) resultFuture.complete(audioBuffer.toByteArray());
                        }
                        case "task-failed" -> {
                            String errorMessage = root.path("header").path("error_message").asText("tts task-failed");
                            if (!resultFuture.isDone()) {
                                resultFuture.completeExceptionally(new IllegalStateException("tts provider task-failed: " + errorMessage));
                            }
                        }
                        default -> { /* result-generated / metadata events: no action needed */ }
                    }
                } catch (Exception parseFailure) {
                    log.warn("tts websocket event was not parseable JSON: {}", parseFailure.getMessage());
                }
            }
        };

        WebSocket webSocket = null;
        try {
            webSocket = httpClient.newWebSocketBuilder()
                .header("Authorization", "bearer " + apiKey)
                .header("X-DashScope-DataInspection", "enable")
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .buildAsync(URI.create(wsUrl), listener)
                .get(timeoutMs, TimeUnit.MILLISECONDS);

            webSocket.sendText(runTaskJson(taskId, preset), true);
            return resultFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            throw new IllegalStateException("tts synthesis timed out after " + timeoutMs + "ms", timeout);
        } finally {
            if (webSocket != null) {
                try { webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done"); } catch (Exception ignored) { /* best-effort */ }
            }
        }
    }

    private String runTaskJson(String taskId, TtsVoicePreset preset) throws Exception {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("text_type", "PlainText");
        parameters.put("voice", preset.providerVoice());
        parameters.put("format", "mp3");
        parameters.put("sample_rate", 22050);
        parameters.put("volume", 50);
        parameters.put("rate", 1);
        parameters.put("pitch", 1);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task_group", "audio");
        payload.put("task", "tts");
        payload.put("function", "SpeechSynthesizer");
        payload.put("model", preset.model());
        payload.put("parameters", parameters);
        payload.put("input", Map.of());

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("action", "run-task");
        header.put("task_id", taskId);
        header.put("streaming", "duplex");

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("header", header);
        message.put("payload", payload);
        return objectMapper.writeValueAsString(message);
    }

    private String continueTaskJson(String taskId, String text) throws Exception {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("action", "continue-task");
        header.put("task_id", taskId);
        header.put("streaming", "duplex");

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("header", header);
        message.put("payload", Map.of("input", Map.of("text", text)));
        return objectMapper.writeValueAsString(message);
    }

    private String finishTaskJson(String taskId) throws Exception {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("action", "finish-task");
        header.put("task_id", taskId);
        header.put("streaming", "duplex");

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("header", header);
        message.put("payload", Map.of("input", Map.of()));
        return objectMapper.writeValueAsString(message);
    }
}
