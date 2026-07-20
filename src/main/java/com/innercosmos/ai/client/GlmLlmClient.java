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

public class GlmLlmClient implements LlmClient {
    private static final Logger log = LoggerFactory.getLogger(GlmLlmClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int timeoutMs;
    private final boolean allowFallback;
    private final String providerName;
    private final AiLogService aiLogService;
    private final Executor aiExecutor;
    private final MockLlmClient fallback;
    private final HttpClient httpClient;

    public GlmLlmClient(String apiKey, String baseUrl, String model, int timeoutMs,
                        AiLogService aiLogService, Executor aiExecutor) {
        this(apiKey, baseUrl, model, timeoutMs, true, "GLM", aiLogService, aiExecutor);
    }

    public GlmLlmClient(String apiKey, String baseUrl, String model, int timeoutMs,
                        boolean allowFallback, String providerName,
                        AiLogService aiLogService, Executor aiExecutor) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl == null || baseUrl.isBlank()
                ? "https://open.bigmodel.cn/api/paas/v4/chat/completions" : baseUrl;
        this.model = model == null || model.isBlank() ? "glm-4-flash" : model;
        this.timeoutMs = timeoutMs <= 0 ? 20000 : timeoutMs;
        this.allowFallback = allowFallback;
        this.providerName = providerName == null || providerName.isBlank() ? "GLM" : providerName;
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
                throw new IllegalStateException("GLM API key is empty");
            }
            response = doChat(request);
            success = true;
            return response;
        } catch (Exception firstError) {
            errorMessage = firstError.getMessage();
            log.warn("GLM chat failed on first attempt, retrying once: {}", firstError.getMessage());
            try {
                response = doChat(request);
                success = true;
                return response;
            } catch (Exception retryError) {
                errorMessage = retryError.getMessage();
                if (!allowFallback) {
                    throw new AiProviderException(providerName + " remote chat failed and fallback is disabled: " + retryError.getMessage());
                }
                log.error("{} chat failed on retry, falling back to mock: {}", providerName, retryError.getMessage());
                response = fallback.chat(request);
                fallbackUsed = true;
                success = true;
                return response;
            }
        } finally {
            aiLogService.recordDetailed(request.userId, request.moduleName, fallbackUsed ? "MOCK" : providerName,
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
                    throw new IllegalStateException("GLM API key is empty");
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
     * Real provider SSE streaming (VS-003 §2). GLM's chat/completions endpoint supports
     * OpenAI-style {@code stream:true} SSE with {@code delta.content} chunks.
     */
    private String streamRemote(LlmRequest request, SseEmitter emitter) throws Exception {
        List<Map<String, String>> messages = buildMessages(request);
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "temperature", request.temperature != null ? request.temperature : 0.72,
                "max_tokens", LlmClient.RESPONSE_MAX_TOKENS,
                "stream", true,
                // Disable the flagship models' deep-thinking phase. With it on, glm-4.6/4.7/5.x
                // spend 20-100s emitting reasoning_content before the answer — unusable for an
                // interactive companion. Off → flagship quality at ~5-6s, and the answer arrives
                // in `content` (no reasoning leaking into our structured-JSON parse). Ignored by
                // non-thinking models (glm-4-flash etc.).
                "thinking", Map.of("type", "disabled")
        );
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "text/event-stream")
                .timeout(Duration.ofMillis(timeoutMs))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        StringBuilder aggregated = new StringBuilder();
        try {
            HttpResponse<java.util.stream.Stream<String>> httpResponse = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofLines());
            if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                // Non-2xx (rate limit / bad request / auth): body is an error JSON, not SSE `data:`
                // lines. Without this guard those lines are all skipped, the stream ends empty, and the
                // caller emits a silent empty `done` -> user sees no reply. Route it into the fallback.
                httpResponse.body().close();
                throw new java.io.IOException(providerName + " stream HTTP " + httpResponse.statusCode());
            }
            httpResponse.body().forEach(line -> {
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
                    log.debug("{} stream parse skip: {}", providerName, parseEx.getMessage());
                }
            });
        } catch (Exception remoteError) {
            log.warn("{} stream failed, falling back to chat drip: {}", providerName, remoteError.getMessage());
            // Only drip the full fallback when nothing was streamed yet, otherwise we would duplicate
            // the partial content already delivered to the client.
            if (aggregated.length() == 0) {
                return dripFromChat(request, emitter);
            }
            return aggregated.toString();
        }
        if (aggregated.length() == 0) {
            // 2xx but no content deltas (empty/malformed stream) -> fall back so the turn still replies.
            log.warn("{} stream returned empty content, falling back to chat drip", providerName);
            return dripFromChat(request, emitter);
        }
        return aggregated.toString();
    }

    /** Blocking-path fallback (retries then local mock) dripped out as stream tokens. */
    private String dripFromChat(LlmRequest request, SseEmitter emitter) throws Exception {
        String full = chat(request);
        for (String token : full.split("")) {
            emitter.send(SseEmitter.event().name("token").data("{\"content\":\"" + escape(token) + "\"}"));
        }
        return full;
    }

    private List<Map<String, String>> buildMessages(LlmRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", request.systemPromptOr(systemPrompt())));
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
        messages.add(Map.of("role", "system", "content", request.systemPromptOr(systemPrompt())));
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
                // See streamRemote: disable deep-thinking so flagship models answer in ~5-6s
                // with a clean `content` field instead of a 20-100s reasoning_content phase.
                "thinking", Map.of("type", "disabled")
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
            throw new RuntimeException("GLM API returned status " + httpResponse.statusCode() + ": " + httpResponse.body());
        }
        JsonNode content = objectMapper.readTree(httpResponse.body()).path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.asText().isBlank()) {
            throw new RuntimeException("Unexpected GLM response format: " + httpResponse.body());
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
