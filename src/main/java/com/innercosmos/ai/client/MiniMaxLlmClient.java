package com.innercosmos.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.exception.AiProviderException;
import com.innercosmos.service.AiLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

public class MiniMaxLlmClient implements LlmClient {
    private static final Logger log = LoggerFactory.getLogger(MiniMaxLlmClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int timeoutMs;
    private final boolean allowFallback;
    private final AiLogService aiLogService;
    private final Executor aiExecutor;
    private final MockLlmClient fallback;
    private final HttpClient httpClient;

    public MiniMaxLlmClient(String apiKey, String baseUrl, String model, int timeoutMs,
                            boolean allowFallback, AiLogService aiLogService, Executor aiExecutor) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl == null || baseUrl.isBlank()
                ? "https://api.minimaxi.com/v1/chat/completions" : baseUrl;
        this.model = model == null || model.isBlank() ? "MiniMax-M3" : model;
        this.timeoutMs = timeoutMs <= 0 ? 30000 : timeoutMs;
        this.allowFallback = allowFallback;
        this.aiLogService = aiLogService;
        this.aiExecutor = aiExecutor;
        this.fallback = new MockLlmClient(aiExecutor);
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    }

    @Override
    public String chat(LlmRequest request) {
        long start = System.currentTimeMillis();
        String response = null;
        boolean success = false;
        boolean fallbackUsed = false;
        String errorMessage = null;
        try {
            if (apiKey == null || apiKey.isBlank()) {
                throw new AiProviderException("MiniMax API key is not configured");
            }
            response = doChat(request);
            success = true;
            return response;
        } catch (Exception firstError) {
            errorMessage = firstError.getMessage();
            log.warn("MiniMax chat failed on first attempt, retrying once: {}", firstError.getMessage());
            try {
                if (apiKey == null || apiKey.isBlank()) {
                    throw firstError;
                }
                response = doChat(request);
                success = true;
                return response;
            } catch (Exception retryError) {
                errorMessage = retryError.getMessage();
                if (!allowFallback) {
                    throw new AiProviderException("MiniMax remote chat failed and fallback is disabled: " + retryError.getMessage());
                }
                log.error("MiniMax chat failed on retry, falling back to mock: {}", retryError.getMessage());
                response = fallback.chat(request);
                fallbackUsed = true;
                success = true;
                return response;
            }
        } finally {
            aiLogService.recordDetailed(request.userId, request.moduleName, fallbackUsed ? "MOCK" : "MINIMAX",
                    fallbackUsed ? "mock-inner-cosmos" : model, request.prompt, response, request.requestJson,
                    response, success, fallbackUsed, fallbackUsed ? errorMessage : (success ? null : errorMessage),
                    System.currentTimeMillis() - start);
        }
    }

    @Override
    public SseEmitter streamChat(LlmRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        aiExecutor.execute(() -> {
            try {
                if (apiKey == null || apiKey.isBlank()) {
                    throw new AiProviderException("MiniMax API key is not configured");
                }
                String aggregated = streamRemote(request, emitter);
                if (aggregated == null) {
                    // streamRemote already completed the emitter via fallback path
                    return;
                }
                emitter.send(SseEmitter.event().name("done").data("{\"message\":\"done\"}"));
                emitter.complete();
            } catch (Exception exception) {
                emitter.completeWithError(exception);
            }
        });
        return emitter;
    }

    /**
     * Real provider SSE streaming (VS-003 §2). Sends {@code stream:true} to MiniMax,
     * parses {@code data:} lines and forwards each {@code delta.content} token over the
     * SseEmitter as it arrives — no long blocking sleep on the request thread (this runs
     * on the async aiExecutor). Returns the aggregated text so callers can log/persist.
     * On provider failure, falls back to a single full-text drip via {@link #chat}.
     */
    private String streamRemote(LlmRequest request, SseEmitter emitter) throws Exception {
        List<Map<String, String>> messages = buildMessages(request);
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "temperature", 0.72,
                "max_tokens", 900,
                "stream", true
        );
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "text/event-stream")
                .timeout(Duration.ofMillis(timeoutMs))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        try {
            java.util.stream.Stream<String> lines = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofLines()).body();
            StringBuilder aggregated = new StringBuilder();
            lines.forEach(line -> {
                if (line == null) return;
                if (!line.startsWith("data:")) return;
                String payload = line.substring(5).trim();
                if (payload.isEmpty() || "[DONE]".equals(payload)) return;
                try {
                    JsonNode delta = objectMapper.readTree(payload)
                            .path("choices").path(0).path("delta").path("content");
                    if (!delta.isMissingNode() && !delta.asText().isEmpty()) {
                        String token = delta.asText();
                        aggregated.append(token);
                        emitter.send(SseEmitter.event().name("token")
                                .data("{\"content\":\"" + escape(token) + "\"}"));
                    }
                } catch (Exception parseEx) {
                    log.debug("MiniMax stream parse skip: {}", parseEx.getMessage());
                }
            });
            return aggregated.toString();
        } catch (Exception remoteError) {
            log.warn("MiniMax stream failed, falling back to chat drip: {}", remoteError.getMessage());
            String full = chat(request);
            for (String token : full.split("")) {
                emitter.send(SseEmitter.event().name("token").data("{\"content\":\"" + escape(token) + "\"}"));
            }
            return full;
        }
    }

    private List<Map<String, String>> buildMessages(LlmRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt()));
        if (request.recentMessages != null) {
            for (String recent : request.recentMessages) {
                if (recent != null && !recent.isBlank()) {
                    messages.add(Map.of("role", "user", "content", "Context note: " + recent));
                }
            }
        }
        messages.add(Map.of("role", "user", "content", request.prompt == null ? "" : request.prompt));
        return messages;
    }

    private String doChat(LlmRequest request) throws Exception {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt()));
        if (request.recentMessages != null) {
            for (String recent : request.recentMessages) {
                if (recent != null && !recent.isBlank()) {
                    messages.add(Map.of("role", "user", "content", "Context note: " + recent));
                }
            }
        }
        messages.add(Map.of("role", "user", "content", request.prompt == null ? "" : request.prompt));
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "temperature", 0.72,
                "max_tokens", 900
        );
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofMillis(timeoutMs))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
            throw new RuntimeException("MiniMax API returned status " + httpResponse.statusCode() + ": " + httpResponse.body());
        }
        JsonNode content = objectMapper.readTree(httpResponse.body()).path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.asText().isBlank()) {
            throw new RuntimeException("Unexpected MiniMax response format: " + httpResponse.body());
        }
        return content.asText();
    }

    private String systemPrompt() {
        return "You are Aurora in Inner Cosmos. Be warm, specific, safe, and reflective. "
                + "Use the user's language. Ask at most one question. Never diagnose.";
    }

    private String escape(String token) {
        return token.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
