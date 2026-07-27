package com.innercosmos.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innercosmos.service.AiLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Native Gemini GenerateContent client.
 *
 * <p>Gemini 3.x exposes its reasoning control as {@code thinkingConfig.thinkingLevel};
 * treating it as an OpenAI-compatible temperature/reasoning field silently loses the
 * fast/minimal versus reflective/high contract used by Aurora's three-stage runtime.
 */
public final class GeminiLlmClient implements LlmClient {
    private static final Logger log = LoggerFactory.getLogger(GeminiLlmClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final String thinkingLevel;
    private final int timeoutMs;
    private final boolean allowFallback;
    private final AiLogService aiLogService;
    private final Executor aiExecutor;
    private final MockLlmClient fallback;
    private final HttpClient httpClient;

    public GeminiLlmClient(String apiKey, String baseUrl, String model, String thinkingLevel,
                           int timeoutMs, boolean allowFallback,
                           AiLogService aiLogService, Executor aiExecutor) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.model = model == null || model.isBlank() ? "gemini-3.6-flash" : model.trim();
        this.thinkingLevel = normalizeThinkingLevel(thinkingLevel, "medium");
        this.timeoutMs = timeoutMs <= 0 ? 30_000 : timeoutMs;
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
        String error = null;
        try {
            if (apiKey.isBlank()) throw new IllegalStateException("Gemini API key is empty");
            response = doChat(request);
            success = true;
            return response;
        } catch (Exception failure) {
            error = failure.getMessage();
            if (!allowFallback) {
                throw new RuntimeException("Gemini remote chat failed and fallback is disabled", failure);
            }
            log.warn("Gemini chat failed; using explicitly labelled local fallback: {}", failure.getMessage());
            response = fallback.chat(request);
            fallbackUsed = true;
            success = true;
            return response;
        } finally {
            if (aiLogService != null) {
                aiLogService.recordDetailed(request.userId, request.moduleName,
                        fallbackUsed ? "MOCK" : "GEMINI",
                        fallbackUsed ? "mock-inner-cosmos" : model,
                        request.prompt, response, request.requestJson, response,
                        success, fallbackUsed, fallbackUsed ? error : (success ? null : error),
                        System.currentTimeMillis() - start);
            }
        }
    }

    @Override
    public SseEmitter streamChat(LlmRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        aiExecutor.execute(() -> {
            try {
                String full = chat(request);
                for (String token : full.split("")) {
                    emitter.send(SseEmitter.event().name("token")
                            .data("{\"content\":\"" + escape(token) + "\"}"));
                }
                emitter.send(SseEmitter.event().name("done").data("{\"message\":\"done\"}"));
                emitter.complete();
            } catch (Exception failure) {
                emitter.completeWithError(failure);
            }
        });
        return emitter;
    }

    private String doChat(LlmRequest request) throws Exception {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(endpoint())
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .timeout(Duration.ofMillis(request.timeoutMsOr(timeoutMs)))
                .POST(HttpRequest.BodyPublishers.ofString(
                        MAPPER.writeValueAsString(requestBody(request))))
                .build();
        HttpResponse<String> httpResponse =
                httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
            throw new RuntimeException("Gemini API returned status " + httpResponse.statusCode());
        }
        JsonNode root = MAPPER.readTree(httpResponse.body());
        String finishReason = root.path("candidates").path(0).path("finishReason").asText("");
        List<String> textParts = new ArrayList<>();
        for (JsonNode part : root.path("candidates").path(0).path("content").path("parts")) {
            if (!part.path("thought").asBoolean(false) && !part.path("text").asText("").isBlank()) {
                textParts.add(part.path("text").asText());
            }
        }
        String text = String.join("", textParts).trim();
        if (text.isBlank()) {
            throw new RuntimeException("Gemini completion contained no usable text"
                    + " (finishReason=" + (finishReason.isBlank() ? "unknown" : finishReason) + ")");
        }
        return text;
    }

    Map<String, Object> requestBody(LlmRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts",
                List.of(Map.of("text", request.systemPromptOr(systemPrompt())))));

        List<Map<String, Object>> contents = new ArrayList<>();
        if (request.recentMessages != null) {
            for (String recent : request.recentMessages) {
                if (recent != null && !recent.isBlank()) {
                    contents.add(content("user", "Context note: " + recent));
                }
            }
        }
        contents.add(content("user", request.prompt == null ? "" : request.prompt));
        body.put("contents", contents);

        Map<String, Object> generation = new LinkedHashMap<>();
        generation.put("maxOutputTokens", request.maxTokensOr(LlmClient.RESPONSE_MAX_TOKENS));
        generation.put("thinkingConfig", Map.of(
                "thinkingLevel", Boolean.TRUE.equals(request.thinkingEnabled)
                        ? normalizeThinkingLevel(request.reasoningEffort, thinkingLevel)
                        : "minimal"));
        body.put("generationConfig", generation);
        return body;
    }

    private Map<String, Object> content(String role, String text) {
        return Map.of("role", role, "parts", List.of(Map.of("text", text)));
    }

    private URI endpoint() {
        return URI.create(baseUrl + "/models/"
                + URLEncoder.encode(model, StandardCharsets.UTF_8) + ":generateContent");
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = value == null || value.isBlank()
                ? "https://generativelanguage.googleapis.com/v1beta" : value.trim();
        return normalized.replaceAll("/+$", "");
    }

    private static String normalizeThinkingLevel(String value, String fallback) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        return switch (normalized) {
            case "minimal", "low", "medium", "high" -> normalized;
            default -> fallback;
        };
    }

    private String systemPrompt() {
        return "You are Aurora in Inner Cosmos. Follow the supplied JSON contract exactly.";
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
