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

public class GlmLlmClient implements LlmClient {
    private static final Logger log = LoggerFactory.getLogger(GlmLlmClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int timeoutMs;
    private final AiLogService aiLogService;
    private final Executor aiExecutor;
    private final MockLlmClient fallback;
    private final HttpClient httpClient;

    public GlmLlmClient(String apiKey, String baseUrl, String model, int timeoutMs,
                        AiLogService aiLogService, Executor aiExecutor) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl == null || baseUrl.isBlank()
                ? "https://open.bigmodel.cn/api/paas/v4/chat/completions" : baseUrl;
        this.model = model == null || model.isBlank() ? "glm-4-flash" : model;
        this.timeoutMs = timeoutMs <= 0 ? 20000 : timeoutMs;
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
                log.error("GLM chat failed on retry, falling back to mock: {}", retryError.getMessage());
                response = fallback.chat(request);
                fallbackUsed = true;
                success = true;
                return response;
            }
        } finally {
            aiLogService.recordDetailed(request.userId, request.moduleName, fallbackUsed ? "MOCK" : "GLM",
                    fallbackUsed ? "mock-inner-cosmos" : model, request.prompt, response, request.requestJson,
                    response, success, fallbackUsed, fallbackUsed ? errorMessage : (success ? null : errorMessage),
                    System.currentTimeMillis() - start);
        }
    }

    @Override
    public SseEmitter streamChat(LlmRequest request) {
        SseEmitter emitter = new SseEmitter(60_000L);
        aiExecutor.execute(() -> {
            try {
                String response = chat(request);
                for (String token : response.split("")) {
                    emitter.send(SseEmitter.event().name("token").data("{\"content\":\"" + escape(token) + "\"}"));
                    Thread.sleep(18);
                }
                emitter.send(SseEmitter.event().name("done").data("{\"message\":\"done\"}"));
                emitter.complete();
            } catch (Exception exception) {
                emitter.completeWithError(exception);
            }
        });
        return emitter;
    }

    private String doChat(LlmRequest request) throws Exception {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt()));
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
