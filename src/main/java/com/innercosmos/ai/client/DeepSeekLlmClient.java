package com.innercosmos.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

public class DeepSeekLlmClient implements LlmClient {
    private static final Logger log = LoggerFactory.getLogger(DeepSeekLlmClient.class);
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

    public DeepSeekLlmClient(String apiKey, String baseUrl, String model, int timeoutMs,
                             boolean allowFallback, AiLogService aiLogService, Executor aiExecutor) {
        this.apiKey = apiKey;
        this.baseUrl = normalizeUrl(baseUrl);
        this.model = model == null || model.isBlank() ? "deepseek-chat" : model;
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
                throw new IllegalStateException("DeepSeek API key is empty");
            }
            response = doChat(request);
            success = true;
            return response;
        } catch (Exception firstError) {
            errorMessage = firstError.getMessage();
            log.warn("DeepSeek chat failed on first attempt: {}", firstError.getMessage());
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
                    throw new RuntimeException("DeepSeek remote chat failed and fallback is disabled", retryError);
                }
                log.error("DeepSeek chat failed on retry, falling back to local mock: {}", retryError.getMessage());
                response = fallback.chat(request);
                fallbackUsed = true;
                success = true;
                return response;
            }
        } finally {
            aiLogService.recordDetailed(request.userId, request.moduleName, fallbackUsed ? "MOCK" : "DEEPSEEK",
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
                    throw new IllegalStateException("DeepSeek API key is empty");
                }
                String aggregated = streamRemote(request, emitter);
                if (aggregated == null) {
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
     * Real provider SSE streaming (VS-003 §2). DeepSeek is OpenAI-compatible.
     */
    private String streamRemote(LlmRequest request, SseEmitter emitter) throws Exception {
        List<Map<String, String>> messages = buildMessages(request);
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "temperature", request.temperature != null ? request.temperature : 0.72,
                "max_tokens", LlmClient.RESPONSE_MAX_TOKENS,
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
                if (line == null || !line.startsWith("data:")) return;
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
                    log.debug("DeepSeek stream parse skip: {}", parseEx.getMessage());
                }
            });
            return aggregated.toString();
        } catch (Exception remoteError) {
            log.warn("DeepSeek stream failed, falling back to chat drip: {}", remoteError.getMessage());
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
                "temperature", request.temperature != null ? request.temperature : 0.72,
                "max_tokens", LlmClient.RESPONSE_MAX_TOKENS,
                "stream", false
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
            throw new RuntimeException("DeepSeek API returned status " + httpResponse.statusCode() + ": " + httpResponse.body());
        }
        JsonNode root = objectMapper.readTree(httpResponse.body());
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.asText().isBlank()) {
            throw new RuntimeException("Unexpected DeepSeek response format: " + httpResponse.body());
        }
        return content.asText();
    }

    private String systemPrompt() {
        return "You are Aurora in Inner Cosmos. Be warm, specific, safe, and reflective. "
                + "Use the user's language. Ask at most one question. Never diagnose.";
    }

    private String normalizeUrl(String value) {
        if (value == null || value.isBlank()) return "https://api.deepseek.com/chat/completions";
        if (value.endsWith("/chat/completions")) return value;
        return value.replaceAll("/+$", "") + "/chat/completions";
    }

    private String escape(String token) {
        return token.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
