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
    private final AiLogService aiLogService;
    private final Executor aiExecutor;
    private final MockLlmClient fallback;

    public MiniMaxLlmClient(String apiKey, String baseUrl, String model, int timeoutMs,
                             AiLogService aiLogService, Executor aiExecutor) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.timeoutMs = timeoutMs;
        this.aiLogService = aiLogService;
        this.aiExecutor = aiExecutor;
        this.fallback = new MockLlmClient(aiExecutor);
    }

    @Override
    public String chat(LlmRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("MiniMax API key is empty, falling back to mock");
            return fallback.chat(request);
        }

        long start = System.currentTimeMillis();
        String response = null;
        boolean success = false;
        try {
            response = doChat(request);
            success = true;
            return response;
        } catch (Exception firstError) {
            log.warn("MiniMax chat failed on first attempt, retrying once: {}", firstError.getMessage());
            try {
                response = doChat(request);
                success = true;
                return response;
            } catch (Exception retryError) {
                log.error("MiniMax chat failed on retry, falling back to mock: {}", retryError.getMessage());
                response = fallback.chat(request);
                success = true;
                return response;
            }
        } finally {
            long latency = System.currentTimeMillis() - start;
            aiLogService.record(request.userId, request.moduleName, request.prompt, response, success, latency);
        }
    }

    @Override
    public SseEmitter streamChat(LlmRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("MiniMax API key is empty, falling back to mock");
            return fallback.streamChat(request);
        }

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
                log.error("MiniMax stream failed: {}", exception.getMessage());
                try {
                    emitter.completeWithError(exception);
                } catch (Exception ignored) {
                }
            }
        });
        return emitter;
    }

    private String doChat(LlmRequest request) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();

        Map<String, Object> systemMsg = Map.of("role", "system", "content", "你是 Aurora，一个朋友式自我整理助手。温和、克制、主动追问一个问题，避免贴标签。");
        Map<String, Object> userMsg = Map.of("role", "user", "content", request.prompt);
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(systemMsg, userMsg),
                "temperature", 0.7,
                "max_tokens", 512
        );

        String jsonBody = objectMapper.writeValueAsString(body);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofMillis(timeoutMs))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> httpResponse = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (httpResponse.statusCode() != 200) {
            throw new RuntimeException("MiniMax API returned status " + httpResponse.statusCode() + ": " + httpResponse.body());
        }

        JsonNode root = objectMapper.readTree(httpResponse.body());
        JsonNode choices = root.get("choices");
        if (choices != null && choices.isArray() && !choices.isEmpty()) {
            JsonNode message = choices.get(0).get("message");
            if (message != null && message.has("content")) {
                return message.get("content").asText();
            }
        }
        throw new RuntimeException("Unexpected MiniMax response format: " + httpResponse.body());
    }

    private String escape(String token) {
        return token.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
